import java.nio.file._

import com.swoval.make._
import sbt._
import sbt.Keys._

object Build {
  val root = (project in file(".")).settings(
    p"foo" :- p"bar" build Files.write(`$@`, Files.readAllBytes(`$<`)),
    TaskKey[Unit]("checkFoo") := {
      val actual = new String(Files.readAllBytes(p"$baseDirectory/foo"))
      val expected = "bar"
      assert(actual == expected, s"'$actual' did not equal '$expected'")
    }
  )
}
