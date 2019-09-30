package com.swoval.make

import java.nio.file.{ Path, Paths }

import sbt.nio.file._
import sbt.nio.file.syntax._

final class Pattern(
    val basePath: Option[Path],
    val prefix: Option[String],
    val suffix: Option[String]
) {
  override def equals(o: Any): Boolean = o match {
    case that: Pattern =>
      this.basePath == that.basePath && this.prefix == that.prefix && this.suffix == that.suffix
    case _ => false
  }
  override def hashCode: Int = (basePath.## ^ prefix.## * 31) * 31 ^ suffix.##
  private def orEmpty(f: Option[_]): Any = f.getOrElse("")
  override lazy val toString: String =
    s"${orEmpty(basePath.map(_ + "/"))}${orEmpty(prefix)}%${orEmpty(suffix)}"
}

object Pattern {
  def rebase(target: Pattern, source: Pattern)(f: (Pattern, Pattern) => Path => Option[Path]) = {
    val absoluteTarget =
      new Pattern(
        Some(target.basePath.getOrElse(Paths.get("")).toAbsolutePath),
        target.prefix,
        target.suffix
      )
    val absoluteSource =
      new Pattern(
        Some(source.basePath.getOrElse(Paths.get("")).toAbsolutePath),
        source.prefix,
        source.suffix
      )
    (absoluteTarget, absoluteSource) -> f(absoluteTarget, absoluteSource)
  }
  implicit class PatternOps(val p: Pattern) {
    def /:(path: Path): Pattern =
      new Pattern(Some(p.basePath.fold(path)(path.resolve)), p.prefix, p.suffix)
    def extension: Option[String] =
      if (hasExtension(p)) p.suffix.map(_.drop(1)) else None
    def toGlob: Glob = {
      val base = p.basePath.map(_.toGlob / **).getOrElse(**)
      p.suffix.map(s => base / s"*$s").getOrElse(base)
    }
    def resolve(path: Path): Pattern = {
      p.basePath match {
        case Some(b) =>
          if (b.isAbsolute) p else new Pattern(Some(path.resolve(b)), p.prefix, p.suffix)
        case _ => new Pattern(Some(path), p.prefix, p.suffix)
      }
    }
    def stem(path: Path): Option[Path] = {
      def relativeStem(relative: String): Option[Path] = {
        val prefix = p.prefix.getOrElse("")
        if (relative.startsWith(prefix)) {
          val suffix = p.suffix.getOrElse("")
          val rest = relative.substring(prefix.length)
          if (rest.endsWith(suffix))
            Some(Paths.get(rest.substring(0, rest.length - suffix.length)))
          else None
        } else None
      }
      p.basePath match {
        case Some(base) if path.startsWith(base) => relativeStem(base.relativize(path).toString)
        case Some(_)                             => None
        case None                                => relativeStem(path.toString)
      }
    }
    def rebase(target: Pattern): Path => Option[Path] = {
      val f: Path => Path = stem => {
        val prefix = target.prefix.getOrElse("")
        val suffix = target.suffix.getOrElse("")
        Paths.get(s"$prefix$stem$suffix")
      }
      rebase(target, f)
    }
    def rebase(target: Pattern, f: Path => Path): Path => Option[Path] = {
      val compatible = target.basePath.isDefined && p.basePath.isDefined &&
        target.prefix.isDefined == p.prefix.isDefined &&
        target.suffix.isDefined == p.suffix.isDefined
      if (!compatible) throw new IllegalArgumentException(s"Couldn't rebase $p to $target")
      path => p.stem(path) map f map target.basePath.get.resolve
    }
  }
  private def hasExtension(pattern: Pattern): Boolean =
    pattern.suffix.fold(false)(s => s.startsWith(".") && s.indexOf('.', 1) == -1)
}
