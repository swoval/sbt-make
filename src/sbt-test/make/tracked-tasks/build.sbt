import java.nio.file._

val foo = taskKey[Path]("foo")
foo :- p"bar.txt" build {
  val content = new String(Files.readAllBytes(`$<`)) + intTask.track
  val count = p"$baseDirectory/count"
  val currentCount = scala.util.Try(new String(Files.readAllBytes(count)).toInt).getOrElse(0)
  Files.write(count, (currentCount + 1).toString.getBytes)
  Files.write(p"$baseDirectory/foo.txt", content.getBytes)
}

TaskKey[String]("bar") build new String(Files.readAllBytes(foo.track))

val intTask = taskKey[Int]("prop")
intTask := 1

val checkContent = inputKey[Unit]("check the file contents")
checkContent := {
  val Seq(name, expected) = Def.spaceDelimited().parsed
  val actual = new String(Files.readAllBytes(p"$baseDirectory/$name"))
  assert(actual == expected)
}
