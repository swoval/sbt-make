package com.swoval.make

import com.swoval.make.impl._
import sbt.Keys._
import sbt.PluginTrigger.AllRequirements
import sbt.Scope.Global
import sbt._
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.Parser._

import scala.language.experimental.macros

object MakePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = AllRequirements
  object autoImport extends MakeKeys with Imports with ExtensionClasses
  private val makeParser: Def.Initialize[State => Parser[() => Any]] = Def.setting { state: State =>
    matched((' ': Parser[Char]).+).examples(" ") ~> MakeParser.forState(state)
  }
  override lazy val projectSettings: Seq[Def.Setting[_]] = super.projectSettings ++ Seq(
    MakeKeys.makeParallelism := java.lang.Runtime.getRuntime.availableProcessors * 2,
  )
  override lazy val globalSettings: Seq[Def.Setting[_]] = super.globalSettings ++ Seq(
    MakeKeys.make := {
      try makeParser.parsed()
      catch {
        case e: RuntimeException =>
          streams.value.log.err(e.toString)
          throw new Exception(e.getMessage, null, true, false) {
            override def toString = e.getMessage
          }
      }
    },
    aggregate in MakeKeys.make := false,
    onLoad := (state => OnLoad.injectMakeSettings((Global / onLoad).value(state))),
    InternalKeys.makeTaskKeysByTarget := Map.empty,
    InternalKeys.makeTaskKeysByTargetPattern := Map.empty,
    InternalKeys.makePhony := false,
    MakeKeys.makePatternMappings := Map.empty,
  )
}
