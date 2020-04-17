import java.nio.file.Files
import java.nio.file.StandardCopyOption

p"target/foo.txt" :- p"foo.md" build
  Files.copy(`$<`, `$@`, StandardCopyOption.REPLACE_EXISTING)

pat"target/%.txt" :- pat"%.md" build
  Files.write(`$@`, (new String(Files.readAllBytes(`$<`)) + "foo").getBytes)

TaskKey[Unit]("check") := {
  val (foo, bar) = (p"target/foo.txt", p"target/bar.txt")
  val Seq(fooContent, barContent) =
    Seq(foo, bar).map(Files.readAllBytes).map(new String(_))
  val (fooExpected, barExpected) = ("foo\n", "bar\nfoo")
  assert(fooContent == fooExpected, s"Content of $foo, '$fooContent', did not equal '$fooExpected'")
  assert(barContent == barExpected, s"Content of $bar, '$barContent', did not equal '$barExpected'")
}
