package com.swoval.make.impl

import java.nio.file.Path

import sbt.nio.FileStamp.Formats

import scala.annotation.implicitNotFound
import scala.reflect.macros.blackbox
import scala.language.experimental.macros

@implicitNotFound("No sjsonnew JsonFormat available for ${T}")
class Format[T](implicit val format: sjsonnew.JsonFormat[T])

object Format extends LowPriorityFormat {
  implicit def default[T](implicit formatter: sjsonnew.JsonFormat[T]): Format[T] = new Format[T]
  implicit val pathFormat: Format[Path] = new Format()(Formats.pathJsonFormatter)
  implicit val seqPathFormat: Format[Seq[Path]] = new Format()(Formats.seqPathJsonFormatter)
}

private[impl] trait LowPriorityFormat {
  implicit def sjsonnewDefault[T]: Format[T] = macro FormatMacros.default[T]
}
object FormatMacros {
  def default[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Format[T]] = {
    import c.universe._
    val tree = q"""
      import sjsonnew.BasicJsonProtocol._
      new com.swoval.make.impl.Format[$weakTypeOf]
    """
    c.Expr[Format[T]](tree)
  }
}
