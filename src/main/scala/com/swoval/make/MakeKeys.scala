package com.swoval.make

import java.nio.file.Path

import sbt.{ inputKey, settingKey }

trait MakeKeys {
  val make = inputKey[Any]("make")
  val makeParallelism = settingKey[Int]("Limits the default number of thread used by make.")
  val makePatternMappings = settingKey[Map[(Pattern, Pattern), Path => Option[Path]]](
    "A function to convert a source matching the right pattern to a target matching the left pattern."
  )
}
object MakeKeys extends MakeKeys
