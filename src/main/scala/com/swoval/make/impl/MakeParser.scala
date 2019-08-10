package com.swoval.make.impl

import java.nio.file.Path
import java.util

import com.swoval.make.impl.OnLoad.FailedTargets
import com.swoval.make.{ MakeKeys, Pattern }
import sbt._
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.Parser._
import sbt.internal.util.complete.Parsers._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try

object MakeParser {
  def forState(state: State): Parser[() => Any] = {
    state
      .get(InternalKeys.makeTargets)
      .flatMap(mt => state.get(InternalKeys.makePatterns).map(mt -> _)) match {
      case Some((mt, mp)) =>
        val extracted = Project.extract(state)
        val project = extracted.currentProject.base.toPath
        val targetParser = forPaths(project, mt) || forPatterns(project, mp)
        val etc = ((evaluateTaskConfig(extracted, state) || targetParser) <~ OptSpace).+
        etc.map { parts => () =>
          val (mkConfig, targets) = parts.map {
            case Left(c)  => (Some(c), None)
            case Right(t) => (None, Some(t))
          }.unzip match {
            case (l, r) => (l.collectFirst { case Some(c) => c }, r.flatten)
          }
          val config = mkConfig
            .map(_())
            .getOrElse(
              evaluateTaskConfig(extracted, state, extracted.get(MakeKeys.makeParallelism))
            )
          targets.foldLeft((state, Nil: List[Path])) {
            case ((s, paths), Left((target, taskKey))) =>
              EvaluateTask(extracted.structure, taskKey, s, extracted.currentRef, config) match {
                case Some((newState, sbt.Value(v))) => (newState, v :: paths)
                case Some((_, Inc(i))) =>
                  extracted.structure.streams(state).use(MakeKeys.make)(fail(target, i, s.log))
                case None => throw new IllegalStateException(s"No task found for $target")
              }
            case ((s, paths), Right((target, taskKey))) =>
              EvaluateTask(extracted.structure, taskKey, s, extracted.currentRef, config) match {
                case Some((newState, sbt.Value(v))) => (newState, v.toList ::: paths)
                case Some((_, Inc(i))) =>
                  extracted.structure.streams(state).use(MakeKeys.make)(fail(target, i, s.log))
                case None => throw new IllegalStateException(s"No task found for $target")
              }
          } match {
            case (_, h :: Nil) => h
            case (_, paths)    => paths.reverse
          }
        }
      case None => Parser.failure("No targets defined in this build")
    }
  }
  private def findDirectCauses(i: Incomplete): Seq[String] = {
    val seen = new util.IdentityHashMap[Incomplete, Incomplete]()
    @tailrec
    def impl(remaining: List[Incomplete], causes: Set[String]): Set[String] = {
      remaining match {
        case h :: tail =>
          seen.put(h, h)
          val remaining = tail ::: h.causes.toList.filterNot(seen.containsKey)
          h.directCause match {
            case Some(fe: FailedTargets) => impl(remaining, causes ++ fe.targets.map(_.toString))
            case _                       => impl(remaining, causes)
          }
        case _ => causes
      }
    }
    impl(i :: Nil, Set.empty[String]).toSeq.sorted
  }
  private def fail(target: String, i: Incomplete, l: Logger): Nothing = {
    findDirectCauses(i) match {
      case Nil => throw new Fail(s"'$target'")
      case causes =>
        import scala.Console.{ RED, RESET }
        val prefix = s"make '$RED$target$RESET' failed due to unmet dependenc"
        val (suffix, fullSuffix) = causes match {
          case h :: Nil => ("y", s"y: $h")
          case list     => (s"ies", s"ies:${list.mkString("\n\t", "\n\t", "")}")
        }
        throw new RuntimeException(prefix + suffix, null, true, false) {
          override def toString: String = prefix + fullSuffix
        }
    }
  }
  def evaluateTaskConfig(extracted: Extracted, state: State): Parser[() => EvaluateTaskConfig] = {
    ((token("-j", "-j [N]") ~> OptSpace ~> '='.? ~> Digit.+) |
      (token("--jobs", "--jobs=N") ~> OptSpace ~> '='.? ~> Digit.+))
      .map { digits =>
        val limit =
          Try(digits.mkString.toInt).toOption.getOrElse(extracted.get(MakeKeys.makeParallelism))
        () => evaluateTaskConfig(extracted, state, limit)
      }
  }

  private class Fail(target: String) extends Throwable(null, null, true, false) {
    override def toString: String = s"Make failure: couldn't build target $target"
  }
  private def evaluateTaskConfig(
      extracted: Extracted,
      state: State,
      limit: Int
  ): EvaluateTaskConfig = {
    val stateConfig = EvaluateTask.extractedTaskConfig(extracted, extracted.structure, state)
    import stateConfig._
    val newRestrictions = restrictions :+ Tags.limit(Settings.concurrency, limit)
    EvaluateTaskConfig(
      newRestrictions,
      checkCycles,
      progressReporter,
      cancelStrategy,
      forceGarbageCollection,
      minForcegcInterval
    )
  }
  def forPaths(base: Path, paths: Map[Path, TaskKey[Path]]): Parser[(String, TaskKey[Path])] = {
    paths.toSeq.collect {
      case (p, n) if p.startsWith(base) => (base.relativize(p).iterator.asScala mkString "/", n)
    } match {
      case Seq((p, k), tail @ _*) =>
        tail.foldLeft(token(p).map(_ => p -> k)) {
          case (p, (t, k)) => p | token(t).map(_ => t -> k)
        }
      case _ => Parser.failure("No single path patterns defined.")
    }
  }

  def forPatterns(
      base: Path,
      patterns: Map[Pattern, TaskKey[Seq[Path]]]
  ): Parser[(String, TaskKey[Seq[Path]])] = {
    patterns.toSeq.collect {
      case (pat, k) if pat.basePath.fold(false)(_.startsWith(base)) =>
        pat.basePath
          .map(p => new Pattern(Some(base.relativize(p)), pat.prefix, pat.suffix).toString)
          .get -> k
    } match {
      case Seq((p, k), tail @ _*) =>
        tail.foldLeft(token(p).map(_ => p -> k)) {
          case (p, (t, k)) => p | token(t).map(_ => t -> k)
        }
      case _ => Parser.failure("No patterns defined.")
    }
  }
}
