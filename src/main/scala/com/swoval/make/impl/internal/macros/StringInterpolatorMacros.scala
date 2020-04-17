package com.swoval.make.impl.internal.macros

import java.io.File
import java.nio.file.{ Path, Paths }

import com.swoval.make.Pattern
import sbt.{ SettingKey, TaskKey }

import scala.reflect.macros.blackbox

object StringInterpolatorMacros {
  def makeImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[String] = {
    import c.universe._
    val parts = args.map(argToAny(c)(_))
    c.macroApplication match {
      case q"$_($_(..${components: Seq[c.Tree] })).$_(..$_)" =>
        val strings: Seq[c.Expr[String]] = components.map(t => c.Expr[String](t))
        strings.tail.zip(parts).foldLeft(strings.head) {
          case (s, (comp, arg)) => reify(s.splice + arg.splice + comp.splice)
        }
    }
  }
  def pathImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Path] = {
    val string = makeImpl(c)(args: _*)
    c.universe.reify {
      val isWin = scala.util.Properties.isWin
      val s = string.splice
      val names = s.replace('\\', '/').split('/')
      val prefix = names.head match {
        case "" if s.startsWith("/") || s.startsWith(File.separator) => File.separator
        case h if isWin && h.endsWith(":")                           => h + File.separator
        case h                                                       => h
      }
      names.tail.foldLeft(Paths.get(prefix))(_ resolve _)
    }
  }
  def patternImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Pattern] = {
    val string = makeImpl(c)(args: _*)
    c.universe.reify {
      val isWin = scala.util.Properties.isWin
      val s = string.splice
      val names = s.replace('\\', '/').split('/')
      val base = names.headOption
        .filterNot(_.contains("%"))
        .map {
          case "" if s.startsWith("/") || s.startsWith(File.separator) => File.separator
          case h if isWin && h.endsWith(":")                           => h + File.separator
          case h                                                       => h
        }
        .map { prefix => names.tail.dropRight(1).foldLeft(Paths.get(prefix))(_ resolve _) }
      val last = names.last
      val parts = last.split('%') match {
        case a if a.isEmpty => Array("")
        case a              => a
      }
      require(parts.length <= 2)
      def orNone(s: String): Option[String] = if (s.isEmpty) None else Some(s)
      new Pattern(base, orNone(parts.head), parts.lift(1).flatMap(orNone))
    }
  }
  private def argToAny[T <: blackbox.Context](c: T)(a: c.Expr[Any]): c.Expr[Any] = {
    import c.universe._
    a.tree match {
      case t @ Literal(Constant(_: String)) =>
        KeyMacros.stringSettingImpl(c)(c.Expr[String](t))
      case t if t.tpe <:< weakTypeOf[TaskKey[_]] =>
        reify(c.Expr[TaskKey[_]](t).splice.value)
      case t if t.tpe <:< weakTypeOf[SettingKey[_]] =>
        reify(c.Expr[SettingKey[_]](t).splice.value)
      case _ => a
    }
  }
}
