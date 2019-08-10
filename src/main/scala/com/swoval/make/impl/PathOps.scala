package com.swoval.make.impl

import java.nio.file.Path

import com.swoval.make.Pattern
import sbt.nio.file.{ Glob, RelativeGlob }

object PathOps {
  def combine(p: Path, pattern: Pattern): Pattern = p /: pattern
  def combine(p: Path, glob: RelativeGlob): Glob = Glob(p, glob)
  def combine(p: Path, string: String): Path = p.resolve(string)
}
