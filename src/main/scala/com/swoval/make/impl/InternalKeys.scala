package com.swoval.make.impl

import java.nio.file.Path

import com.swoval.make.{ Dependency, Pattern, AutomaticVariables }
import sbt.internal.util.AttributeKey
import sbt.{ Def, Incomplete, Task, TaskKey, settingKey, taskKey }
import sbt.nio.file.Glob

trait InternalKeys {
  private[make] type BulkResult = Either[(Path, Incomplete), (Path, Path)]
  val makeTarget = settingKey[Path]("The target of a make task")
  val makeTask = settingKey[DependentTask[_]]("A task key and implementation")
  val makeTargetPattern = settingKey[Pattern]("The target pattern of a wildcard make task")
  val makeSourcePattern =
    settingKey[Pattern]("The firstDependencyImpl pattern of a wildcard make task")
  val makeTargetGlob = settingKey[Glob]("The target glob of a wildcard make task")
  val makeSourceGlob = settingKey[Glob]("The firstDependencyImpl glob of a wildcard make task")
  val makeDependencies = settingKey[Seq[Dependency]]("Dependencies for task")
  val makeDependentTasks = settingKey[Seq[TaskDependency[_]]]("Task dependencies for a make task")
  val makeWildcards = taskKey[(Path, Option[Path]) => AutomaticVariables]("make wildcards")
  val makePhony = settingKey[Boolean]("Indicates whether the make task is phony")
  val makeIncrementalSourceExpr = taskKey[String]("The string representation of the implementation")
  val makeIncremental =
    settingKey[AutomaticVariables => Def.Initialize[Task[Any]]]("Incremental task evaluation")
  val makeNullImplementation = taskKey[Nothing]("null").withRank(Int.MaxValue)
  val bulkMakeIncremental =
    settingKey[(Seq[Path], TaskKey[_]) => Def.Initialize[Task[Seq[BulkResult]]]](
      "Bulk incremental task evaluation"
    )
  val makeTaskKeysByTarget =
    settingKey[Map[Path, TaskKey[Path]]](
      "For a given target, return the task key that generates it."
    )
  val makeTaskKeysByTargetPattern =
    settingKey[Map[Pattern, TaskKey[Seq[Path]]]](
      "For a given pattern, return the task key that generates the matching files."
    )
  val makeTargetDependencies =
    taskKey[Seq[Path]]("The stamps for the target dependencies.")
  private[swoval] val makeTargets = AttributeKey[Map[Path, TaskKey[Path]]]("make-target")
  private[swoval] val makePatterns =
    AttributeKey[Map[Pattern, TaskKey[Seq[Path]]]]("make-patterns")
  val wildcardTarget =
    AttributeKey[Path]("current-target", "The current target in a wildcard task.")
  val wildcardSource =
    AttributeKey[Path](
      "current-firstDependencyImpl",
      "The current firstDependencyImpl file in a wildcard task."
    )
}
object InternalKeys extends InternalKeys
