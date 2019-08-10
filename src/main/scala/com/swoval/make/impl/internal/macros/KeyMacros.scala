package com.swoval.make.impl.internal.macros

import sbt.{ Def, SettingKey, TaskKey }

import scala.reflect.macros.blackbox

object KeyMacros {
  def stringSettingImpl(c: blackbox.Context)(key: c.Expr[String]): c.Expr[String] =
    c.universe.reify(SettingKey[String](lowerFirstChar(key.splice)).value)
  def settingImpl[T](c: blackbox.Context)(key: c.Expr[SettingKey[T]]): c.Expr[T] =
    c.universe.reify(key.splice.value)
  def taskImpl[T](c: blackbox.Context)(key: c.Expr[TaskKey[T]]): c.Expr[T] =
    c.universe.reify(key.splice.value)
  def lowerFirstChar(s: String): String = s match {
    case k if k.head.isUpper => s"${k.head.toLower}${k.substring(1)}"
    case k                   => k
  }
  def stringSetting[T: c.WeakTypeTag](c: blackbox.Context)(
      init: c.Expr[T]
  ): c.Expr[Def.Setting[String]] = {
    import c.universe._
    c.macroApplication match {
      case q"$_(${k @ Literal(Constant(_: String)) }).:=[$_]({$_})  " =>
        val key = c.Expr[String](k)
        reify {
          SettingKey[String](lowerFirstChar(key.splice), "", Int.MaxValue) := {
            init.splice.toString
          }
        }
    }
  }
}
