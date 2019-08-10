package com.swoval.make.impl

import java.nio.file.Path

import com.swoval.make
import com.swoval.make.impl.internal.macros.{ KeyMacros, SettingMacros }
import sbt.{ SettingKey, TaskKey }

import scala.language.dynamics
import scala.language.experimental.macros

private[make] trait Imports {
  type MakePattern = make.Pattern
  val MakePattern = make.Pattern
  def `$^`: Seq[Path] = macro SettingMacros.depsImpl
  def `$?`: Seq[Path] = macro SettingMacros.changesImpl
  def `$@`: Path = macro SettingMacros.targetImpl
  def `$<`: Path = macro SettingMacros.firstDependencyImpl
  def sh(args: String): Unit = macro SettingMacros.sh
  object % extends Dynamic {
    def selectDynamic(extension: String): MakePattern = new MakePattern(None, None, Some(extension))
  }
  object $ {
    def apply(key: String): String = macro KeyMacros.stringSettingImpl
    def apply[T](key: TaskKey[T]): T = macro KeyMacros.taskImpl[T]
    def apply[T](key: SettingKey[T]): T = macro KeyMacros.settingImpl[T]
  }
}
