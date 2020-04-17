package com.swoval.make

import java.nio.file.Path

import com.swoval.make.impl.Imports

final class AutomaticVariables private (
    val target: Path,
    val dependencies: Seq[Path],
    val changes: Seq[Path]
) {
  def firstDependency: Path = dependencies.headOption match {
    case None    => throw new IllegalStateException(s"No source dependencies exist for $target")
    case Some(h) => h
  }
  override def toString: String =
    s"AutomaticVariables(target: $target, dependencies: $dependencies, changes: $changes)"
}
object AutomaticVariables {
  private class WrappedSeq(val s: Seq[Path]) extends Seq[Path] {
    override def length: Int = s.length
    override def apply(idx: Int): Path = s.apply(idx)
    override def iterator: Iterator[Path] = s.iterator
    override lazy val toString: String = s mkString " "
  }
  def apply(target: Path, dependencies: Seq[Path], changes: Seq[Path]): AutomaticVariables =
    new AutomaticVariables(target, new WrappedSeq(dependencies), new WrappedSeq(changes))

  /**
   * This will get removed in a macro transformation but is so that, for example,
   * Imports.`$^` can be expanded inside of a build block.
   *
   * @return nothing
   */
  implicit def default: AutomaticVariables = ???
}
