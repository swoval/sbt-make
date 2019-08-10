package com.swoval.make

import java.nio.file.{ Path, Paths }

import com.swoval.make.impl.PathHelpers
import sbt.{ Scope, TaskKey }

sealed trait Dependency
object Dependency {
  implicit class PathDependency(val path: Path) extends Dependency
  implicit class PatternDependency(val pattern: Pattern) extends Dependency
  implicit class DependencyOps(val d: Dependency) extends AnyVal {
    def taskKey(s: Scope): TaskKey[_] = d match {
      case p: PathDependency    => PathHelpers.taskKey(p.path, s)
      case p: PatternDependency => PathHelpers.taskKey(p.pattern, s)
    }
    def resolve(base: Path): Dependency = d match {
      case p: PathDependency if p.path.isAbsolute => p
      case p: PathDependency                      => new PathDependency(base.resolve(p.path))
      case p: PatternDependency =>
        p.pattern.basePath match {
          case Some(path) if path.isAbsolute => p
          case b =>
            new PatternDependency(
              new Pattern(
                Some(base.resolve(b.getOrElse(Paths.get("")))),
                p.pattern.prefix,
                p.pattern.suffix
              )
            )
        }
    }
  }
}
