package com.swoval.make.impl

import java.io.File
import java.nio.file.Path

import com.swoval.make.Pattern
import sbt.internal.util.AttributeMap
import sbt.{ Def, Scope, Select, Task, TaskKey, Zero }

object PathHelpers {
  private[this] val sanitizer = java.util.regex.Pattern.compile("[^a-zA-Z0-9_\\-]")
  def makeValue(k: TaskKey[Path]): Def.Initialize[Task[Path]] = Def.task(k.value)
  def taskKey(path: Path, scope: Scope): TaskKey[Path] = {
    addToString(TaskKey[Path](pathToSettingName(path), "", Int.MaxValue), scope, path)
  }
  def fileTaskKey(path: Path, scope: Scope): TaskKey[File] = {
    addToString(TaskKey[File](pathToSettingName(path), "", Int.MaxValue), scope, path)
  }
  def taskKey(target: Pattern, scope: Scope): TaskKey[Seq[Path]] = {
    val base = TaskKey[Seq[Path]](pathNameToSettingName(target.toString), "", Int.MaxValue)
    addToString(base, scope, target)
  }

  private def addToString[T](taskKey: TaskKey[T], scope: Scope, any: AnyRef): TaskKey[T] = {
    val am = AttributeMap.empty.put(Scope.customShowString, any.toString)
    taskKey in scope.copy(task = Zero, extra = Select(am))
  }
  def taskKey(path: Path): TaskKey[Path] = TaskKey[Path](pathToSettingName(path), "", Int.MaxValue)
  def taskKey(target: Pattern): TaskKey[Seq[Path]] =
    TaskKey[Seq[Path]](pathNameToSettingName(target.toString), "", Int.MaxValue)
  def pathToSettingName(path: Path): String = pathNameToSettingName(path.toString)
  def pathNameToSettingName(pathname: String): String =
    sanitizer
      .matcher(
        "make__" + pathname
          .replace("/", "_slash_")
          .replace("\\\\", "_slash_")
          .replace("-", "_dash_")
          .replace(".", "_dot_")
          .replace("%", "_percent_")
          .replace("*", "_star_")
      )
      .replaceAll("x")
}
