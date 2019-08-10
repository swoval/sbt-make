import java.nio.file._

val foo = taskKey[String]("foo")
foo :- p"files/foo.txt" build new String(Files.readAllBytes(`$<`))

val bar = taskKey[String]("bar")
bar := foo.value

val checkValue =
  settingKey[(TaskKey[String], String) => Def.Initialize[Task[Unit]]]("check the value of a task")
checkValue := { (tk: TaskKey[String], expected: String) =>
  Def.task {
    val actual = tk.value
    assert(actual == expected, s"'$actual' != '$expected' for ${tk.key.label}")
  }
}

InputKey[Unit]("checkFoo") := Def.inputTaskDyn {
  checkValue.value(foo, Def.spaceDelimited().parsed.mkString)
}.evaluated
InputKey[Unit]("checkBar") := Def.inputTaskDyn {
  checkValue.value(bar, Def.spaceDelimited().parsed.mkString)
}.evaluated
