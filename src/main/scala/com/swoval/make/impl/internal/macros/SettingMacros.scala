package com.swoval.make.impl
package internal.macros

import java.nio.file.{ Files, Path, Paths }
import java.util

import com.swoval.make.{ AutomaticVariables, Dependency, Pattern }
import sbt._
import sbt.Keys.baseDirectory

import scala.collection.JavaConverters._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object SettingMacros {
  def makeTaskImpl[R: c.WeakTypeTag](c: blackbox.Context)(
      f: c.Expr[R]
  ): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    c.macroApplication match {
      case q"(${Typed(q"new $_(${taskKeyE: c.Tree }, ${depsE: c.Tree })(${formatE: c.Tree })", _) }).build($_)" =>
        val taskKey = c.Expr[TaskKey[R]](taskKeyE)
        val deps = c.Expr[Seq[Dependency]](depsE)
        val format = c.Expr[Format[R]](formatE)
        val incremental = makeTask(c)(f, taskKey, deps, format)
        reify(
          (taskKey.splice := InternalKeys.makeNullImplementation
            .map(_ => ???)
            .value) +: incremental.splice
        )
      case _ =>
        c.abort(c.enclosingPosition, "Incompatible macro application")
    }
  }
  def directTaskImpl[R: c.WeakTypeTag](
      c: blackbox.Context
  )(f: c.Expr[R]): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    c.macroApplication match {
      case q"$_(${taskKeyE: c.Tree })(${formatE: c.Tree }).build[$_]($_)" =>
        val taskKey = c.Expr[TaskKey[R]](taskKeyE)
        val deps = reify(Nil: Seq[Dependency])
        val format = c.Expr[Format[R]](formatE)
        val incremental = makeTask(c)(f, taskKey, deps, format)
        reify(
          (taskKey.splice := InternalKeys.makeNullImplementation
            .map(_ => ???)
            .value) +: incremental.splice
        )
      case _ =>
        c.abort(c.enclosingPosition, "Incompatible macro application")
    }
  }
  private[this] def makeTask[R: c.WeakTypeTag](c: blackbox.Context)(
      f: c.Expr[R],
      taskKey: c.Expr[TaskKey[R]],
      deps: c.Expr[Seq[Dependency]],
      format: c.Expr[Format[R]]
  ): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    val fileName = PathHelpers.pathNameToSettingName(c.enclosingPosition.source.file.name)
    val name = c.Expr[String](Literal(Constant(fileName)))
    val number = c.Expr[Int](Literal(Constant(c.enclosingPosition.line)))
    val task = reify(TaskKey[Any](s"make__${name.splice}_${number.splice}", "", Int.MaxValue))
    val (stripped, dependentTasks) = stripWildcards[R](c)(f.tree)
    val impl = reify({ implicit automatic: AutomaticVariables => Def.task(stripped.splice) })
    val taskDeps =
      reify((task.splice / InternalKeys.makeDependentTasks := dependentTasks.splice) :: Nil)
    val strippedE = c.Expr[String](Literal(Constant(stripped.toString)))
    val res = reify {
      (task.splice / InternalKeys.makeIncrementalSourceExpr := strippedE.splice) ::
        ((task.splice / InternalKeys.makeTask) :=
          new DependentTask(
            taskKey.splice in taskKey.splice.scope
              .copy(project = sbt.Select(sbt.Keys.thisProjectRef.value)),
            impl.splice
          )(format.splice)) :: ((task.splice / InternalKeys.makeDependencies) := {
        val base = baseDirectory.value.toPath
        deps.splice.map(_.resolve(base))
      }) :: (task.splice := InternalKeys.makeNullImplementation.value) :: taskDeps.splice
    }
    c.Expr[Seq[Def.Setting[_]]](c.untypecheck(res.tree))
  }
  def makePathImpl[R](c: blackbox.Context)(
      f: c.Expr[Any]
  ): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    c.macroApplication match {
      case q"((${Apply(t, (pathE: c.Tree) :: (depsE: c.Tree) :: Nil) }: $_)).$_[$_]($_)" =>
        val path = c.Expr[Path](pathE)
        val deps = c.Expr[Seq[Dependency]](depsE)
        makePath(c)(f, path, deps, phony = t.tpe <:< weakTypeOf[PhonyMakePathTask])
    }
  }
  def directPathImpl(
      c: blackbox.Context
  )(f: c.Expr[Any]): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    c.macroApplication match {
      case q"$_(${pathE: c.Tree }).build($_)" =>
        val path = c.Expr[Path](pathE)
        makePath(c)(f, path, reify(Nil: Seq[Dependency]), phony = false)
      case q"$_(${pathE: c.Tree }).phony.build($_)" =>
        val path = c.Expr[Path](pathE)
        makePath(c)(f, path, reify(Nil: Seq[Dependency]), phony = true)
      case _ =>
        c.abort(c.enclosingPosition, "Incompatible macro application")
    }
  }
  private[this] def makePath[R](c: blackbox.Context)(
      f: c.Expr[Any],
      path: c.Expr[Path],
      deps: c.Expr[Seq[Dependency]],
      phony: Boolean
  ): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    val fileName = PathHelpers.pathNameToSettingName(c.enclosingPosition.source.file.name)
    val name = c.Expr[String](Literal(Constant(fileName)))
    val number = c.Expr[Int](Literal(Constant(c.enclosingPosition.line)))
    val task = reify(TaskKey[Any](s"make__${name.splice}_${number.splice}", "", Int.MaxValue))
    val (impl, dependentTasks) = incImpl(c)(f.tree, task)

    val taskDeps =
      reify((task.splice / InternalKeys.makeDependentTasks := dependentTasks.splice) :: Nil)
    val stripped = c.Expr[String](Literal(Constant(impl.toString)))
    val phonyE = c.Expr[Boolean](Literal(Constant(phony)))
    val resolvedPath = reify {
      path.splice match {
        case p if p.isAbsolute => p
        case p                 => sbt.Keys.baseDirectory.value.toPath.resolve(p)
      }
    }
    val res = reify {
      (task.splice / InternalKeys.makeIncrementalSourceExpr := stripped.splice) ::
        (task.splice / InternalKeys.makeTarget := resolvedPath.splice) ::
        (task.splice / InternalKeys.makePhony := phonyE.splice) ::
        ((task.splice / InternalKeys.makeDependencies) := {
          val base = baseDirectory.value.toPath
          deps.splice.map(_.resolve(base))
        }) :: impl.splice :: taskDeps.splice
    }
    c.Expr[Seq[Def.Setting[_]]](c.untypecheck(res.tree))
  }
  private type IncImpl = AutomaticVariables => Def.Initialize[Task[Any]]
  private def incImpl(c: blackbox.Context)(
      tree: c.Tree,
      task: c.Expr[TaskKey[Any]]
  ): (c.Expr[Setting[IncImpl]], c.Expr[Seq[TaskDependency[_]]]) = {
    val (wildcardsRemoved, deps) = stripWildcards[Any](c)(tree)
    (c.universe.reify((task.splice / InternalKeys.makeIncremental) := { implicit wildcards =>
      Files.createDirectories(wildcards.target.getParent)
      val path = baseDirectory.value.toPath.relativize(wildcards.target).toString
      Def.task[Any](wildcardsRemoved.splice)(t => t.named(path)) tag Settings.concurrency
    }), deps)
  }
  private def stripWildcards[T](
      c: blackbox.Context
  )(tree: c.Tree): (c.Expr[T], c.Expr[Seq[TaskDependency[_]]]) = {
    import c.universe._
    val firstType = weakTypeOf[AutomaticVariables]
    val result = new util.Vector[c.Expr[TaskDependency[_]]]
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.universe.Tree): c.universe.Tree = tree match {
        case Apply(TypeApply(q"scala.Predef.implicitly", w :: Nil), _) if w.tpe <:< firstType =>
          q"scala.Predef.implicitly[$w]"
        case q"$_(${tk: c.Tree })(${fmt: c.Tree }).track" =>
          val taskKey = c.Expr[TaskKey[Any]](tk)
          val format = c.Expr[Format[Any]](fmt)
          result.add(reify(new TaskDependency(taskKey.splice, format.splice.format)))
          q"$tk.value"
        case t => super.transform(t)
      }
    }

    val transformed = transformer.transform(tree)
    val taskDeps = result.asScala
    val taskDepsExpr =
      if (taskDeps.isEmpty) reify(Nil: Seq[TaskDependency[_]])
      else {
        val base = reify((taskDeps.head.splice :: Nil): List[TaskDependency[_]])
        taskDeps.tail.foldLeft(base) { case (r, d) => reify(d.splice :: r.splice) }
      }
    (c.Expr[T](transformed), taskDepsExpr)
  }
  def patternTaskImpl[R](c: blackbox.Context)(
      f: c.Expr[Any]
  ): c.Expr[Seq[Def.Setting[_]]] = {
    import c.universe._
    c.macroApplication match {
      case Apply(TypeApply(Select(Typed(Apply(_, tpE :: spE :: depsE :: Nil), _), _), _), _) =>
        val targetPattern = c.Expr[Pattern](tpE)
        val sourcePattern = c.Expr[Pattern](spE)
        val deps = c.Expr[Seq[Dependency]](depsE)
        val fileName = c.enclosingPosition.source.file.name
          .replaceAll("/", "_slash_")
          .replaceAll("\\\\", "_slash_")
          .replaceAll("[^a-zA-Z0-9_\\-]", "x")
        val name = c.Expr[String](Literal(Constant(fileName)))
        val number = c.Expr[Int](Literal(Constant(c.enclosingPosition.line)))
        val task = reify(TaskKey[Any](s"make__${name.splice}_${number.splice}", "", Int.MaxValue))
        val (impl, dependentTasks) = incImpl(c)(f.tree, task)
        val taskDeps =
          reify((task.splice / InternalKeys.makeDependentTasks := dependentTasks.splice) :: Nil)
        val stripped = c.Expr[String](Literal(Constant(impl.toString)))
        val res = reify {
          ((task.splice / InternalKeys.makeTargetPattern) := {
            val base = baseDirectory.value.toPath
            targetPattern.splice.resolve(base)
          }) ::
            ((task.splice / InternalKeys.makeSourcePattern) := {
              val base = baseDirectory.value.toPath
              sourcePattern.splice.resolve(base)
            }) ::
            (task.splice / InternalKeys.makeIncrementalSourceExpr := stripped.splice) ::
            ((task.splice / InternalKeys.makeDependencies) := {
              val base = baseDirectory.value.toPath
              deps.splice.map(_.resolve(base))
            }) ::
            impl.splice :: taskDeps.splice
        }
        c.Expr[Seq[Def.Setting[_]]](c.untypecheck(res.tree))
    }
  }
  def patternImpl(
      c: blackbox.Context
  )(pattern: c.Expr[Pattern], dependencies: c.Expr[Dependency]*): c.Expr[MakePatternTask] = {
    import c.universe._
    val targetPattern = c.Expr[Pattern](c.macroApplication match {
      case Apply(Select(Apply(_, pattern :: Nil), _), _) => pattern
      case _                                             => c.abort(c.enclosingPosition, "Incompatible macro application site")
    })
    val p = dependencies.reverse match {
      case Seq(h, rest @ _*) =>
        rest.foldLeft(reify(h.splice :: Nil)) { case (a, p) => reify(p.splice :: a.splice) }
      case _ => reify(Nil)
    }
    reify(new MakePatternTask(targetPattern.splice, pattern.splice, p.splice))
  }
  def pathTaskImpl(c: blackbox.Context)(dependencies: c.Expr[Dependency]*): c.Expr[MakePathTask] = {
    import c.universe._
    val targetPath = c.Expr[Path](c.macroApplication match {
      case Apply(Select(Apply(_, (t: c.Tree) :: Nil), _), _) => t
      case _                                                 => c.abort(c.enclosingPosition, "Incompatible macro application site")
    })
    val p = dependencies.reverse match {
      case Seq(h, rest @ _*) =>
        rest.foldLeft(reify(h.splice :: Nil)) { case (a, p) => reify(p.splice :: a.splice) }
      case _ => reify(Nil)
    }
    reify(new MakePathTask(targetPath.splice, p.splice))
  }
  def phonyTaskImpl(
      c: blackbox.Context
  )(dependencies: c.Expr[Dependency]*): c.Expr[PhonyMakePathTask] = {
    import c.universe._
    val targetPath = c.macroApplication match {
      case q"$_(${t: c.Tree }).phony.:-($_)" if t.tpe <:< weakTypeOf[String] =>
        val str = c.Expr[String](t)
        reify(Paths.get(str.splice))
      case q"$_(${t: c.Tree }).phony.:-($_)" if t.tpe <:< weakTypeOf[Path] =>
        c.Expr[Path](t)
      case _ => c.abort(c.enclosingPosition, "Incompatible macro application site")
    }
    val p = dependencies.reverse match {
      case Seq(h, rest @ _*) =>
        rest.foldLeft(reify(h.splice :: Nil)) { case (a, p) => reify(p.splice :: a.splice) }
      case _ => reify(Nil)
    }
    reify(new PhonyMakePathTask(targetPath.splice, p.splice))
  }

  def taskImpl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Dependency]*): c.Expr[MakeTask[T]] = {
    import c.universe._
    val (taskKey, format) = c.macroApplication match {
      case q"($_(${taskKey: c.Tree })(${format: c.Tree })).:-($_)" =>
        (c.Expr[TaskKey[T]](taskKey), c.Expr[Format[T]](format))
      case Apply(q"($_(${taskKey: c.Tree })(${format: c.Tree })).:-", _) =>
        (c.Expr[TaskKey[T]](taskKey), c.Expr[Format[T]](format))
      case _ =>
        c.abort(c.enclosingPosition, "Incompatible macro application site")
    }
    val p = dependencies.reverse match {
      case Seq(h, rest @ _*) =>
        rest.foldLeft(reify(h.splice :: Nil)) { case (a, p) => reify(p.splice :: a.splice) }
      case _ => reify(Nil)
    }
    reify(new MakeTask(taskKey.splice, p.splice)(format.splice))
  }
  def depsImpl(c: blackbox.Context): c.Expr[Seq[Path]] = wildCard[Seq[Path]](c)("dependencies")
  def changesImpl(c: blackbox.Context): c.Expr[Seq[Path]] = wildCard[Seq[Path]](c)("changes")
  def targetImpl(c: blackbox.Context): c.Expr[Path] = wildCard[Path](c)("target")
  def firstDependencyImpl(c: blackbox.Context): c.Expr[Path] = wildCard[Path](c)("firstDependency")
  private def wildCard[T](c: blackbox.Context)(name: String): c.Expr[T] = {
    import c.universe._
    c.Expr[T](q"implicitly[${weakTypeOf[AutomaticVariables]}].${TermName(name)}")
  }

  def makePathValue(c: blackbox.Context): c.Expr[Path] = {
    import c.universe._
    c.macroApplication match {
      case q"$_(${pathTree: c.Tree }).$_" =>
        val name = c.Expr[Path](pathTree)
        reify {
          val base: Path = (baseDirectory in ThisProject).value.toPath
          val path: Path = name.splice
          val rebased: Path = if (path.isAbsolute) path else base.resolve(path)
          val map = InternalKeys.makeTaskKeysByTarget.value
          val key = map(rebased)
          val extracted = sbt.Project.extract(sbt.Keys.state.value)
          extracted.runTask(key, sbt.Keys.state.value)._2
        }
    }
  }
  def makePatternValue(c: blackbox.Context): c.Expr[Seq[Path]] = {
    import c.universe._
    c.macroApplication match {
      case q"$_(${pathTree: c.Tree }).$_" =>
        val name = c.Expr[Pattern](pathTree)
        reify {
          val base: Path = (baseDirectory in ThisProject).value.toPath
          val pattern: Pattern = name.splice
          val rebased: Path = pattern.basePath
            .map { p => if (p.isAbsolute) p else base.resolve(p) }
            .getOrElse(base)
          val newPattern = new Pattern(Some(rebased), pattern.prefix, pattern.suffix)
          val map = (InternalKeys.makeTaskKeysByTargetPattern in ThisProject).value
          val key = map(newPattern)
          val extracted = sbt.Project.extract(sbt.Keys.state.value)
          extracted.runTask(key, sbt.Keys.state.value)._2
        }
    }
  }
  def sh(c: blackbox.Context)(args: c.Expr[String]): c.Expr[Unit] =
    c.universe.reify(com.swoval.make.Shell(Some(sbt.Keys.streams.value.log), args.splice))
}
