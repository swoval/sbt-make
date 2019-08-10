import java.nio.file._

p"$baseDirectory/foo.out" :- p"$baseDirectory/foo.in" build {
  Files.copy(`$<`, `$@`, StandardCopyOption.REPLACE_EXISTING)
}

InputKey[Unit]("checkFoo") := {
  val expected = Def.spaceDelimited("").parsed.head
  assert(new String(Files.readAllBytes(p"$baseDirectory/foo.out".make)) == expected)
}

pat"pattern/out/%.md" :- pat"pattern/in/%.txt" build {
  Files.copy(`$<`, `$@`, StandardCopyOption.REPLACE_EXISTING)
}

InputKey[Unit]("checkPattern") := {
  val expected = Def.spaceDelimited("").parsed.map(_.split("->").toSeq).sortBy(_.head)
  val results = pat"pattern/out/%.md".make.map { p: Path =>
    Seq(p.getFileName.toString, new String(Files.readAllBytes(p)))
  }.sortBy(_.head)
  assert(results == expected, s"$results != $expected")
}

p"blah" :- p"pattern/out/foo.md" build {
  Files.copy(`$<`, `$@`, StandardCopyOption.REPLACE_EXISTING)
}

InputKey[Unit]("checkBlah") := {
  val expected = Def.spaceDelimited("").parsed.head
  assert(new String(Files.readAllBytes(p"blah".make)) == expected)
  assert(new String(Files.readAllBytes(p"pattern/out/foo.md".make)) == expected)
}
