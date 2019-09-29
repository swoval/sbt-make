package com.swoval.make.impl

import java.io.File
import java.nio.file.{ Files, Path }

import scala.collection.JavaConverters._

package object test {
  def withTempDir[R](f: Path => R): R = {
    val tempDir = Files.createTempDirectory("test")
    try f(tempDir)
    finally {
      Files.walk(tempDir).iterator.asScala.toSeq.reverse.foreach(Files.deleteIfExists)
      Files.deleteIfExists(tempDir)
    }
  }
  implicit class StringOps(val s: String) extends AnyVal {
    def +/(o: String): String = s"$s/$o"
  }
  object / {
    def apply(s: String): String = '/' + s
  }
}
