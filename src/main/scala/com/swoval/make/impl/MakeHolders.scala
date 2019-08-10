package com.swoval.make.impl

import java.nio.file.Path

import com.swoval.make.impl.internal.macros.SettingMacros.{
  makePathImpl,
  makeTaskImpl,
  patternTaskImpl
}
import com.swoval.make.{ Dependency, Pattern }
import sbt.{ Def, TaskKey }

import scala.language.experimental.macros

class MakePathTask(val target: Path, val dependencies: Seq[Dependency]) {
  def build[R](f: => R): Seq[Def.Setting[_]] = macro makePathImpl[R]
}
class PhonyMakePathTask(val target: Path, val dependencies: Seq[Dependency]) {
  def build[R](f: => R): Seq[Def.Setting[_]] = macro makePathImpl[R]
}
class MakePatternTask(
    val target: Pattern,
    val source: Pattern,
    val dependencies: Seq[Dependency]
) {
  def build[R](f: => R): Seq[Def.Setting[_]] = macro patternTaskImpl[R]
}
class MakeTask[T: Format](val key: TaskKey[T], val dependencies: Seq[Dependency]) {
  def format: Format[T] = implicitly
  def build(f: => T): Seq[Def.Setting[_]] = macro makeTaskImpl[T]
}
