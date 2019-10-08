import java.nio.file.{ Files, Path }
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

val foo = taskKey[Path]("foo")
foo :- p"foo" build Files.copy(`$<`, baseDirectory.value.toPath / "foo.txt", REPLACE_EXISTING)

val bar = taskKey[Path]("bar")
bar := Files.write(baseDirectory.value.toPath / "bar.txt", "bar".getBytes)

foo := foo.dependsOn(bar).value

TaskKey[Unit]("check") := {
  assert(foo.value == baseDirectory.value.toPath / "foo.txt")
  assert(new String(Files.readAllBytes(foo.value)) == "foo")
  val barFile = baseDirectory.value.toPath / "bar.txt"
  assert(new String(Files.readAllBytes(barFile)) == "bar")
}
