package com.swoval.make.impl

import java.nio.file.{ Files, Path }

import com.swoval.make.impl.test._
import org.scalatest.FlatSpec
import sbt.TaskKey
import sbt.internal.util.complete.Parser
import sbt.nio.file.syntax._

class MakeParserSpec extends FlatSpec {
  import com.swoval.make.MakePlugin.autoImport._
  val x = sbt.taskKey[Int]("x")

  p"foo".phony :- p"${"FOO"}/bar" build {
    x.track
  }

  "Parser" should "parse files" in withTempDir { dir =>
    val subdir = Files.createDirectories(dir / "subdir")
    val subfile1 = Files.createFile(subdir / "file1")
    val subfile2 = Files.createFile(subdir / "file2")
    val nested = Files.createDirectories(subdir / "nested")
    val nestedFile = Files.createFile(nested / "file")
    val files = subfile1 :: subfile2 :: nestedFile :: Nil
    val parser = MakeParser.forPaths(dir, files.map(f => f -> TaskKey[Path](s"make__$f")).toMap)

    def completions(prefix: String): Set[(String, String)] =
      Parser.completions(parser, prefix, 0).get.map(c => (c.display, c.append))
    def completionsForPath(prefix: String, path: Path): Option[(String, String)] = {
      dir.relativize(path).toString.replace('\\', '/') match {
        case s if s startsWith prefix => Some((s, s.drop(prefix.length)))
        case _                        => None
      }
    }
    def completionsFor(prefix: String): Set[(String, String)] =
      Seq(subfile1, subfile2, nestedFile).flatMap(completionsForPath(prefix, _)).toSet
    assert(completions("subdir") == completionsFor("subdir"))
    assert(
      completions("subdir").map(_._2) == Set(/("file1"), /("file2"), /("nested") +/ "file")
    )
    assert(completions("subdir" +/ "nest") == completionsFor("subdir" +/ "nest"))
    assert(completions("subdir" +/ "nest").map(_._2) == Set("ed/file"))
  }
}
