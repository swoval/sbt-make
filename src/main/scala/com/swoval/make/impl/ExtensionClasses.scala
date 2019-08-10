package com.swoval.make.impl

import java.nio.file.{ Path, Paths }

import com.swoval.make.impl.ExtensionClasses.{
  MakeStringContext,
  SettingGlobOps,
  SettingPathOps,
  SettingPatternOps,
  SettingStringOps,
  TaskKeyOps
}
import com.swoval.make.impl.internal.macros.{ KeyMacros, SettingMacros, StringInterpolatorMacros }
import com.swoval.make.{ Dependency, Pattern }
import sbt.Def.Setting
import sbt.TaskKey
import sbt.nio.file.Glob.GlobOps
import sbt.nio.file.{ Glob, RecursiveGlob, RelativeGlob }

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros

trait ExtensionClasses {
  implicit def pathToSettingMacroPathOps(p: Path): SettingPathOps = new SettingPathOps(p)
  implicit def pathToSettingMacroPatternOps(p: Pattern): SettingPatternOps =
    new SettingPatternOps(p)
  implicit def globToSettingMacroGlobOps(g: Glob): SettingGlobOps = new SettingGlobOps(g)
  implicit def recGlobToSettingMacroGlobOps(g: RecursiveGlob.type): SettingGlobOps =
    new SettingGlobOps(g)
  implicit def stringToSettingOps(s: String): SettingStringOps = new SettingStringOps(s)
  implicit def taskKeyOps[T: Format](tk: TaskKey[T]): TaskKeyOps[T] = new TaskKeyOps(tk)
  implicit def makeStringOps(sc: StringContext): MakeStringContext = new MakeStringContext(sc)
  def path(pathname: String): Path = Paths.get(pathname)
  def glob(glob: String): Glob = Glob(glob)
}
object ExtensionClasses {
  implicit class SettingStringOps(val s: String) extends AnyVal {
    def :=[T](init: T): Setting[String] = macro KeyMacros.stringSetting[T]
    def phony: PhonyPathOps = new PhonyPathOps(Paths.get(s))
  }
  implicit class TaskKeyOps[T: Format](val tk: TaskKey[T]) {
    def :-(dependencies: Dependency*): MakeTask[T] = macro SettingMacros.taskImpl[T]
    def build[R](f: => R): Seq[Setting[_]] = macro SettingMacros.directTaskImpl[T]
    @compileTimeOnly("Track can only be used inside of a make `build` block")
    def track: T = ???
  }
  implicit class SettingPathOps(val p: Path) extends AnyVal {
    def /(relativeGlob: RelativeGlob): Glob = Glob(s"$p/$relativeGlob")
    def /(pattern: Pattern): Pattern = {
      new Pattern(Some(pattern.basePath.fold(p)(p.resolve)), pattern.prefix, pattern.suffix)
    }
    def rename(f: String => String): Path = {
      val name = Paths.get(f(p.getFileName.toString))
      Option(p.getParent).map(_.resolve(name)).getOrElse(name)
    }
    def make: Path = macro SettingMacros.makePathValue
    def :-(dependencies: Dependency*): MakePathTask = macro SettingMacros.pathTaskImpl
    def build(f: => Any): Seq[Setting[_]] = macro SettingMacros.directPathImpl
    def phony: PhonyPathOps = new PhonyPathOps(p)
  }
  class PhonyPathOps(val p: Path) extends AnyVal {
    def :-(dependencies: Dependency*): PhonyMakePathTask = macro SettingMacros.phonyTaskImpl
    def build(f: => Any): Seq[Setting[_]] = macro SettingMacros.directPathImpl
  }
  implicit class SettingPatternOps(val p: Pattern) extends AnyVal {
    def make: Seq[Path] = macro SettingMacros.makePatternValue
    def :-(pattern: Pattern, dependencies: Dependency*): MakePatternTask =
      macro SettingMacros.patternImpl
  }
  implicit class MakeStringContext(val s: StringContext) extends AnyVal {
    def p(args: Any*): Path = macro StringInterpolatorMacros.pathImpl
    def m(args: Any*): String = macro StringInterpolatorMacros.makeImpl
    def pat(args: Any*): Pattern = macro StringInterpolatorMacros.patternImpl
  }
  implicit class SettingGlobOps(val g: Glob) extends AnyVal {
    def /(component: String): Glob = (g: GlobOps) / component
    def /(rel: RelativeGlob): Glob = (g: GlobOps) / rel
  }
}
