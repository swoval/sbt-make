package com.swoval.make.impl

import com.swoval.make.AutomaticVariables
import sbt.{ Def, Task, TaskKey }

import scala.language.experimental.macros

class DependentTask[T](
    val tk: TaskKey[T],
    val impl: AutomaticVariables => Def.Initialize[Task[T]]
)(implicit val format: Format[T]) {
  override def equals(obj: Any): Boolean = obj match {
    case that: DependentTask[_] => this.tk == that.tk && this.impl == that.impl
    case _                      => false
  }
  override def hashCode(): Int = tk.## | (31 * impl.##)
  override def toString: String = s"DependentTask($tk, $impl)"
}

class TaskDependency[T](val taskKey: TaskKey[T], val format: sjsonnew.JsonFormat[T])
