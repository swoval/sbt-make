package com.swoval.make.impl

import java.nio.file.{ Files, Path, Paths }
import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.swoval.make.Dependency.{ PathDependency, PatternDependency }
import com.swoval.make.MakeKeys.makePatternMappings
import com.swoval.make.impl.InternalKeys._
import com.swoval.make.{ AutomaticVariables, Pattern }
import sbt._
import sbt.internal.util.AttributeKey
import sbt.nio.FileStamp
import sbt.nio.Keys._
import sbt.util.Logger
import sjsonnew.BasicJsonProtocol.StringJsonFormat
import sjsonnew.JsonFormat

import scala.collection.JavaConverters._

object OnLoad {
  private[make] val makeInjectInProgress = AttributeKey[AtomicBoolean]("make-init")
  private[this] def addTaskDefinition[T](setting: Def.Setting[Task[T]]): Def.Setting[Task[T]] =
    setting.mapInit((sk, task) => Task(task.info.set(sbt.Keys.taskDefinitionKey, sk), task.work))
  val injectMakeSettings = { state: State =>
    state.get(makeInjectInProgress) match {
      case Some(m) if m.get =>
        m.set(false)
        state
      case _ =>
        val extracted = Project.extract(state)
        val (pathTargetScopes, patternTargetScopes, taskScopes) = getScopes(extracted)
        val (pathDependencies, patternDependencies) =
          getDependencies(
            extracted,
            pathTargetScopes.asScala.toSeq ++ patternTargetScopes.asScala ++ taskScopes.asScala
          )
        val pathTargets = getPathTargets(extracted, pathTargetScopes)
        val patternTargets = getPatternTargets(extracted, patternTargetScopes)
        val patterns = patternTargets.keys.map(t => t -> t.toGlob).toSeq
        val base: (
            Seq[(Path, Scope)],
            Seq[(Path, Pattern, (TaskKey[Path], TaskKey[Seq[BulkResult]]))]
        ) = (Nil, Nil)
        val (undefined, inputPatterns) = pathDependencies.asScala.foldLeft(base) {
          case (r, (t, _)) if pathTargets.contains(t) => r
          case ((p, patMap), s @ (t, _)) =>
            patterns.find { case (_, glob) => glob.matches(t) } match {
              case Some((pat, _)) =>
                val (_, _, scope) = patternTargets(pat)
                val incrementalKeyName = PathHelpers.pathNameToSettingName(s"$t.__inc")
                val incrementalKey =
                  TaskKey[Seq[BulkResult]](incrementalKeyName, "", Int.MaxValue)
                (p, patMap :+ (t, pat, PathHelpers.taskKey(t, scope) -> incrementalKey))
              case _ => (p :+ s, patMap)
            }
        }
        val keys = undefined.map { case (p, s) => p -> PathHelpers.fileTaskKey(p, s) }.toMap
        val undefinedPatterns = patternDependencies.asScala.collect {
          case (p, s) if !patternTargets.contains(p) => p -> PathHelpers.taskKey(p, s)
        }.toSeq
        val undefinedSettings = keys.flatMap(singleFileDependency).toVector ++
          undefinedPatterns.flatMap(singlePatternDependency)

        val regularTargets: Map[Path, TaskKey[_]] = keys ++ pathTargets.mapValues(_._1)
        val singlePatterns =
          inputPatterns.flatMap(
            singleFilePatternSettings(_, patternTargets, regularTargets, extracted)
          )
        val allTargets = regularTargets ++ inputPatterns.map { case (t, _, (tk, _)) => t -> tk }
        val patternIndividualFiles = inputPatterns.groupBy(_._2).map {
          case (pat, targets) => pat -> targets.map { case (t, _, (_, bk)) => t -> bk }
        }
        val allPatternTargets = patternTargets.mapValues(_._2) ++ undefinedPatterns
        val path =
          pathDependencySettings(pathTargets, allPatternTargets, allTargets, extracted)
        val pattern = patternDependencySettings(
          patternTargets,
          regularTargets,
          allPatternTargets,
          patternIndividualFiles,
          extracted
        )
        val task = taskSettings(taskScopes, regularTargets, allPatternTargets, extracted)
        val allExtra = undefinedSettings ++ singlePatterns ++ path ++ pattern ++ task
        if (allExtra.nonEmpty) {
          val targetMappings = pathTargets.mapValues(_._1)
          val patternMappings = patternTargets.mapValues(_._2)
          val injectInProgress = new AtomicBoolean(true)
          val updatedState = state
            .put(makeTargets, targetMappings)
            .put(makePatterns, patternMappings)
            .put(makeInjectInProgress, injectInProgress)
            .addExitHook(() => injectInProgress.set(false))
          extracted.appendWithSession(
            allExtra :+ (Keys.onLoadMessage := "Injected sbt-make settings."),
            updatedState
          )
        } else state
    }
  }

  /**
   * This uses TaskKey[File] to prevent sbt from injecting the outputFileStamps setting. This
   * allows us to override the outputFileStamps key, avoiding recalculation of the file hash.
   */
  private def singleFileDependency: ((Path, TaskKey[File])) => List[Def.Setting[_]] = {
    case (path: Path, key: TaskKey[File]) =>
      (key := {
        val _ = key.inputFileChanges
        if (!Files.exists(path)) {
          throw new Throwable(null, null, true, false) {
            override def toString: String = s"No rule to make target: $path"
          }
        } else path.toFile
      }) :: (key / outputFileStamps := {
        val _ = key.value
        (key / inputFileStamps).value
      }) :: (key / fileInputs := path.toGlob :: Nil) :: Nil
  }
  private def singlePatternDependency: ((Pattern, TaskKey[Seq[Path]])) => List[Def.Setting[_]] = {
    case (pattern: Pattern, key: TaskKey[Seq[Path]]) =>
      (key := key.inputFiles) :: (key / fileInputs := pattern.toGlob :: Nil) :: Nil
  }

  private def taskSettings(
      scopes: util.Set[Scope],
      targets: Map[Path, TaskKey[_]],
      patternDependencies: Map[Pattern, TaskKey[Seq[Path]]],
      extracted: Extracted
  ): Seq[Def.Setting[_]] = {
    scopes.asScala.toSeq.map { scope =>
      val dependencies = extracted.get(scope / makeDependencies)
      extracted.get(scope / makeTask) match {
        case t: DependentTask[r] =>
          type T = r
          val tk: TaskKey[T] = t.tk
          val format: JsonFormat[T] = t.format.format
          val target = t.tk.key.label
          val stamps = PathHelpers.pathNameToSettingName(s"$target.__stamps")
          val stampsKey = TaskKey[Seq[(Path, FileStamp)]](stamps, "", Int.MaxValue)
          val impl: AutomaticVariables => Def.Initialize[Task[T]] = t.impl
          val taskChanges = anyTaskChanges(extracted.get(scope / makeDependentTasks))
          val phony = extracted.get(scope / makePhony)
          val res = addTaskDefinition(scope / outputFileStamps := {
            dependencies
              .flatMap {
                case p: PathDependency =>
                  targets.get(p.path).map(_ / outputFileStamps)
                case p: PatternDependency =>
                  patternDependencies.get(p.pattern).map(_ / outputFileStamps)
              }
              .join
              .map(_.flatten)
              .value
          }) :: (stampsKey := {
            val _ = tk.value
            (scope / outputFileStamps).value
          }) :: (stampsKey := stampsKey.triggeredBy(tk).value) ::
            addTaskDefinition(tk := Def.taskDyn {
              val previousOutputs = stampsKey.previous
              val outputChanges = DiffStamps(_, (scope / outputFileStamps).value)
              val outputDeps = (scope / outputFileStamps).value.map(_._1)
              val changes =
                previousOutputs.map(outputChanges).getOrElse(FileChanges.noPrevious(outputDeps))
              val implChanges =
                Previous.runtimeInEnclosingTask(scope / makeIncrementalSourceExpr).value match {
                  case Some(s) => (scope / makeIncrementalSourceExpr).value != s
                  case _       => true
                }
              val anyChanges = phony || implChanges || taskChanges.value || changes.hasChanges
              Previous.runtimeInEnclosingTask(tk)(format).value match {
                case Some(t) if !anyChanges => Def.task[T](t)
                case _ =>
                  impl(AutomaticVariables(null, outputDeps, changes.created ++ changes.modified)).result
                    .map {
                      case Value(v) => v
                      case Inc(i)   => throw cleanup(null, i, sbt.Keys.streams.value.log)
                    }
              }
            }.value) :: Nil
          res
      }
    }.flatten
  }
  private def anyTaskChanges(tasks: Seq[TaskDependency[_]]): Def.Initialize[Task[Boolean]] =
    tasks
      .map {
        case td: TaskDependency[t] =>
          type T = t
          val prev =
            Previous.runtimeInEnclosingTask(td.taskKey: TaskKey[T])(td.format: JsonFormat[T])
          td.taskKey.zip(prev) {
            case (v, o) => v.flatMap(value => o.map(!_.contains(value)))
          }
        case _ => throw new IllegalStateException("shutup intellij")
      }
      .join
      .flatMap(joinTasks(_).join.map(_.exists(_ == true)))
  private def pathDependencySettings(
      targets: Map[Path, (TaskKey[Path], Scope)],
      patterns: Map[Pattern, TaskKey[Seq[Path]]],
      singleFiles: Map[Path, TaskKey[_]],
      extracted: Extracted
  ): Seq[Def.Setting[_]] = {
    targets.toVector.flatMap {
      case (target, (tk, scope)) =>
        val dependencies = extracted.get(scope / makeDependencies)
        val stamps = PathHelpers.pathNameToSettingName(s"$target.__stamps")
        val stampsKey = TaskKey[Seq[(Path, FileStamp)]](stamps, "", Int.MaxValue)
        val impl = extracted.get(scope / makeIncremental)
        val taskChanges = anyTaskChanges(extracted.get(scope / makeDependentTasks))
        val phony = extracted.get(scope / makePhony)
        addTaskDefinition(scope / outputFileStamps := {
          dependencies
            .flatMap {
              case p: PathDependency =>
                targets
                  .get(p.path)
                  .map(_._1 / outputFileStamps) orElse singleFiles
                  .get(p.path)
                  .map(_ / outputFileStamps)
              case p: PatternDependency =>
                patterns.get(p.pattern).map(_ / outputFileStamps)
            }
            .join
            .map(_.flatten)
            .value
        }) :: (stampsKey := {
          tk.value
          (scope / outputFileStamps).value
        }) :: (stampsKey := stampsKey.triggeredBy(tk).value) ::
          addTaskDefinition(tk := Def.taskDyn {
            val previousOutputs = stampsKey.previous
            val outputChanges = DiffStamps(_, (scope / outputFileStamps).value)
            val outputDeps = (scope / outputFileStamps).value.map(_._1)
            val changes =
              previousOutputs.map(outputChanges).getOrElse(FileChanges.noPrevious(outputDeps))
            val implChanges =
              Previous.runtimeInEnclosingTask(scope / makeIncrementalSourceExpr).value match {
                case Some(s) => (scope / makeIncrementalSourceExpr).value != s
                case _       => true
              }
            val anyChanges = phony || implChanges || changes.hasChanges || taskChanges.value
            if (!Files.exists(target) || anyChanges) {
              Option(target.getParent).foreach(Files.createDirectories(_))
              val av = AutomaticVariables(target, outputDeps, changes.created ++ changes.modified)
              impl(av).result.map {
                case _: Value[_] => target
                case Inc(i)      => throw cleanup(target, i, sbt.Keys.streams.value.log)
              }
            } else {
              Def.task(target)
            }
          }.value) :: (makeTaskKeysByTarget in projectScope(scope) += target -> tk) :: Nil
    }
  }
  private def singleFilePatternSettings(
      input: (Path, Pattern, (TaskKey[Path], TaskKey[Seq[BulkResult]])),
      patterns: Map[Pattern, (Pattern, TaskKey[Seq[Path]], Scope)],
      targets: Map[Path, TaskKey[_]],
      extracted: Extracted
  ): Seq[Def.Setting[_]] = {
    val (target, targetPattern, (tk, incrementalKey)) = input
    patterns.get(targetPattern) match {
      case Some((sourcePattern, _, scope)) =>
        val stamps = PathHelpers.pathNameToSettingName(s"$target.__stamps")
        val stampsKey = TaskKey[Seq[(Path, FileStamp)]](stamps, "", Int.MaxValue)
        val bulkIncrementalKey = scope / bulkMakeIncremental
        val f = patternMapping(extracted, scope, targetPattern, sourcePattern)
        addTaskDefinition(incrementalKey := Def.taskDyn {
          val inputs =
            tk.inputFiles.flatMap(s => f(s).collect { case t if t == target => s })
          bulkIncrementalKey.value(inputs, stampsKey)
        }.value) :: (stampsKey / inputFileStamps := {
          val current = (tk / inputFileStamps).value
          val failed = incrementalKey.value.collect { case Left((p, _)) => p }.toSet
          current.filterNot { case (p, _) => failed(p) }
        }) :: trigger(stampsKey / inputFileStamps, incrementalKey) ::
          (tk / fileInputs := sourcePattern.toGlob :: Nil) ::
          addTaskDefinition {
            tk := (incrementalKey.value match {
              case Seq(Right((p, _))) => p
              case Seq(Left((p, i)))  => throw cleanup(p, i, sbt.Keys.streams.value.log)
            })
          } :: (makeTaskKeysByTarget in projectScope(scope) += target -> tk) :: Nil
      case None => Nil
    }
  }
  private def patternBulkSettings(
      patterns: Map[Pattern, (Pattern, TaskKey[Seq[Path]], Scope)],
      targets: Map[Path, TaskKey[_]],
      patternDependencies: Map[Pattern, TaskKey[Seq[Path]]],
      extracted: Extracted
  ): Seq[Def.Setting[_]] = {
    patterns.toVector.flatMap {
      case (targetPattern, (sourcePattern, tk, scope)) =>
        val dependencies = extracted.get(scope / makeDependencies)
        val stamps = PathHelpers.pathNameToSettingName(s"$targetPattern.__stamps")
        val stampsKey = TaskKey[Seq[(Path, FileStamp)]](stamps, "", Int.MaxValue)
        val incrementalKey = scope / bulkMakeIncremental
        val impl = extracted.get(scope / makeIncremental)
        val taskChanges = anyTaskChanges(extracted.get(scope / makeDependentTasks))

        val f = patternMapping(extracted, scope, targetPattern, sourcePattern)
        addTaskDefinition(scope / outputFileStamps := {
          dependencies
            .flatMap {
              case p: PathDependency => targets.get(p.path).map(_ / outputFileStamps)
              case p: PatternDependency =>
                patternDependencies.get(p.pattern).map(_ / outputFileStamps)
            }
            .join
            .map(_.flatten)
            .value
        }) :: (stampsKey := {
          val _ = tk.value
          (scope / outputFileStamps).value
        }) :: (stampsKey := stampsKey.triggeredBy(tk).value) ::
          (incrementalKey := { (sources: Seq[Path], stampKey: TaskKey[_]) =>
            Def.taskDyn {
              val previousOutputs = stampsKey.previous
              val outputChanges = DiffStamps(_, (scope / outputFileStamps).value)
              val outputDeps = (scope / outputFileStamps).value.map(_._1)
              val fileChanges =
                previousOutputs
                  .map(outputChanges)
                  .getOrElse(FileChanges.noPrevious(outputDeps))
              val implChanges =
                Previous.runtimeInEnclosingTask(scope / makeIncrementalSourceExpr).value match {
                  case Some(s) => (scope / makeIncrementalSourceExpr).value != s
                  case _       => true
                }
              val forceUpdate = implChanges || fileChanges.hasChanges || taskChanges.value
              val modifiedOutputs = fileChanges.created ++ fileChanges.modified
              val inputChanges = (tk / changedInputFiles).value
              val allInputChanges = (stampKey / inputFileStamps).previous
                .map(inputChanges)
                .getOrElse(FileChanges.noPrevious(tk.inputFiles))
              val deleted = allInputChanges.deleted.toSet
              val updated = (allInputChanges.created ++ allInputChanges.modified).toSet
              sources
                .flatMap(s => f(s).map(s -> _))
                .flatMap {
                  case (source, target) if deleted(source) =>
                    Files.deleteIfExists(target)
                    None
                  case (source, target) if updated(source) || forceUpdate =>
                    val wildcards =
                      AutomaticVariables(
                        target,
                        source +: outputDeps,
                        (updated ++ modifiedOutputs).toSeq
                      )
                    Option(target.getParent).foreach(Files.createDirectories(_))
                    Some(impl(wildcards).result.map {
                      case Value(_) => Right(target, source): BulkResult
                      case Inc(i) =>
                        Left(source, cleanup(target, i, sbt.Keys.streams.value.log)): BulkResult
                    })

                  case (source, target) =>
                    Some(Def.task(Right(target, source): BulkResult))
                }
                .join
                .flatMap(x => joinTasks(x).join)
            }
          }) :: Nil
    }
  }
  private def trigger[T](task: TaskKey[T], triggeredBy: TaskKey[_]): Def.Setting[Task[T]] =
    task := task.triggeredBy(triggeredBy).value
  private def patternDependencySettings(
      patterns: Map[Pattern, (Pattern, TaskKey[Seq[Path]], Scope)],
      targets: Map[Path, TaskKey[_]],
      patternDependencies: Map[Pattern, TaskKey[Seq[Path]]],
      explicitFiles: Map[Pattern, Seq[(Path, TaskKey[Seq[BulkResult]])]],
      extracted: Extracted
  ): Seq[Def.Setting[_]] = {
    patternBulkSettings(patterns, targets, patternDependencies, extracted) ++
      patterns.toVector.flatMap {
        case (targetPattern, (sourcePattern, tk, scope)) =>
          val f = patternMapping(extracted, scope, targetPattern, sourcePattern)
          val explicit = explicitFiles.getOrElse(targetPattern, Nil)
          val glob = targetPattern.toGlob
          val excludes = targets.view.filter { case (p, _) => glob.matches(p) }.toVector
          val excludePaths = excludes.view.map(_._1).toSet
          val excludeTasks = excludes.map(_._2)
          val stamps = PathHelpers.pathNameToSettingName(s"$targetPattern.__stamps")
          val stampsKey = TaskKey[Seq[(Path, FileStamp)]](stamps, "", Int.MaxValue)
          val incrementalKey = scope / bulkMakeIncremental
          val bulkIncrementalKeyName =
            PathHelpers.pathNameToSettingName(s"$targetPattern.__inc")
          val bulkIncrementalKey =
            TaskKey[Seq[BulkResult]](bulkIncrementalKeyName, "", Int.MaxValue)
          val explicitTasks = explicit.map(_._2).join.map(_.flatten)
          val explicitPaths = explicit.map(_._1).toSet ++ excludePaths
          addTaskDefinition {
            bulkIncrementalKey := Def.taskDyn {
              val filteredInputs = tk.inputFiles.flatMap { s =>
                f(s).collect { case t if !explicitPaths(t) => s }
              }
              Seq(explicitTasks, incrementalKey.value(filteredInputs, stampsKey)).join
                .flatMap(joinTasks(_).join.map(_.flatten))
            }.value
          } ::
            addTaskDefinition(tk := Def.taskDyn {
              val excludePathTasks = excludeTasks
                .map {
                  case t if classOf[Path].isAssignableFrom(tk.key.manifest.runtimeClass) =>
                    t.asInstanceOf[TaskKey[Path]]
                  case t if classOf[File].isAssignableFrom(tk.key.manifest.runtimeClass) =>
                    t.asInstanceOf[TaskKey[File]].map(_.toPath)
                }
                .join
                .flatMap(joinTasks(_).join)
              Seq(excludePathTasks, bulkIncrementalKey.map { results =>
                results.map {
                  case Right((p, _)) => p
                  case Left((_, i))  => throw i
                }
              }).join.flatMap(joinTasks(_).join.map(_.flatten))
            }.value) :: (stampsKey / inputFileStamps := {
            val current = (tk / inputFileStamps).value
            val failed = bulkIncrementalKey.value.collect {
              case Left((p, _)) => p
            }.toSet
            current.filterNot { case (p, _) => failed(p) }
          }) :: trigger(stampsKey / inputFileStamps, bulkIncrementalKey) ::
            (tk / fileInputs := sourcePattern.toGlob :: Nil) ::
            (makeTaskKeysByTargetPattern in projectScope(scope) += {
              targetPattern -> tk
            }) :: Nil
      }
  }
  private def projectScope(scope: Scope): Scope =
    scope.copy(config = Zero, task = Zero, extra = Zero)
  private def getScopes(
      extracted: Extracted
  ): (util.Set[Scope], util.Set[Scope], util.Set[Scope]) = {
    val pathTargets = new util.HashSet[Scope]
    val patternTargets = new util.HashSet[Scope]
    val tasks = new util.HashSet[Scope]
    extracted.structure.settings.foreach {
      case s if s.key.key == makeTarget.key        => pathTargets.add(s.key.scope)
      case s if s.key.key == makeTargetPattern.key => patternTargets.add(s.key.scope)
      case s if s.key.key == makeTask.key          => tasks.add(s.key.scope)
      case _                                       =>
    }
    (pathTargets, patternTargets, tasks)
  }
  private def getPathTargets(
      extracted: Extracted,
      scopes: util.Set[Scope]
  ): Map[Path, (TaskKey[Path], Scope)] =
    scopes.asScala.map { scope =>
      val target = resolve(extracted.get(scope / makeTarget), scope, extracted)
      target -> (PathHelpers.taskKey(target, scope), scope)
    }.toMap

  private def getPatternTargets(
      extracted: Extracted,
      scopes: util.Set[Scope]
  ): Map[Pattern, (Pattern, TaskKey[Seq[Path]], Scope)] =
    scopes.asScala.map { scope =>
      val targetPattern = resolve(extracted.get(scope / makeTargetPattern), scope, extracted)
      val sourcePattern = resolve(extracted.get(scope / makeSourcePattern), scope, extracted)
      (targetPattern, (sourcePattern, PathHelpers.taskKey(targetPattern, scope), scope))
    }.toMap
  private def resolve(path: Path, scope: Scope, extracted: Extracted): Path = {
    path match {
      case p if p.isAbsolute => p
      case p =>
        val base = scope.project match {
          case Select(ProjectRef(uri, _)) if uri.getScheme == "file" => Paths.get(uri.getPath)
          case _                                                     => extracted.currentProject.base.toPath
        }
        base.resolve(p)
    }
  }
  private def resolve(pattern: Pattern, scope: Scope, extracted: Extracted): Pattern = {
    pattern.basePath match {
      case Some(p) if p.isAbsolute => pattern
      case p =>
        val basePath = resolve(p.getOrElse(Paths.get("")), scope, extracted)
        new Pattern(basePath = Some(basePath), prefix = pattern.prefix, suffix = pattern.suffix)
    }
  }
  private def getDependencies(
      extracted: Extracted,
      scopes: Seq[Scope]
  ): (util.Set[(Path, Scope)], util.Set[(Pattern, Scope)]) = {
    val pathDependencies = new util.HashSet[(Path, Scope)]
    val patternDependencies = new util.HashSet[(Pattern, Scope)]
    scopes.foreach { scope =>
      extracted.get(scope / makeDependencies).map {
        case p: PathDependency =>
          pathDependencies.add((resolve(p.path, scope, extracted), scope.copy(task = Zero)))
        case p: PatternDependency =>
          patternDependencies.add((resolve(p.pattern, scope, extracted), scope.copy(task = Zero)))
      }
    }
    (pathDependencies, patternDependencies)
  }
  private def cleanup(t: Path, i: Incomplete, logger: Logger): Incomplete = {
    val cause = (Seq(i.directCause).flatten ++ i.causes.flatMap(_.directCause)).headOption
    cause match {
      case Some(c) =>
        logStackTrace(logger, c, t)
        i.copy(directCause = Some(new MakeException(t, c)))
      case _ => i
    }
  }
  private def logStackTrace(logger: Logger, throwable: Throwable, t: Path): Unit = {
    val stack = throwable.getStackTrace
    val stripped = stack.indexWhere(_.getFileName.contains("TypeFunctions")) match {
      case -1 | 0 => stack
      case i      => stack.take(i - 1)
    }
    synchronized {
      Option(t).foreach(target => logger.error(s"Failed to build target: $target"))
      logger.err(throwable.toString)
      stripped.foreach(e => logger.err(s"\tat $e"))
    }
  }
  private def patternMapping(
      extracted: Extracted,
      scope: Scope,
      target: Pattern,
      source: Pattern
  ): Path => Option[Path] =
    extracted.get(scope / makePatternMappings).getOrElse((target, source), source.rebase(target))

  private def pluralize(targets: List[Path]): String = targets match {
    case Nil      => ""
    case h :: Nil => s"$h"
    case t        => s"targets:\n${t.mkString("\t", "\n\t", "")}"
  }
  private[make] class FailedTargets(val targets: List[Path])
      extends Throwable(null, null, true, false) {
    override def toString: String = failureMessage
    def failureMessage: String = s"Make failed to build ${pluralize(targets)}"
  }
  private[make] class MakeException(val target: Path, cause: Throwable)
      extends Throwable(null, cause, true, false) {
    override def toString: String = ""
  }
}
