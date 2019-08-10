import java.nio.file.Files

p"bar" :- p"bar.txt" build {
  val bytes = Files.readAllBytes(`$<`)
  Files.write(`$@`, bytes)
}
p"blah" :- (p"bar", p"foo.txt") build {
  val content = `$^`.map(Files.readAllBytes).map(new String(_)).mkString
  Files.write(`$@`, content.getBytes)
}

InputKey[Unit]("checkContents") := {
  val Seq(file, contents) = Def.spaceDelimited("").parsed
  val path = baseDirectory.value.toPath.resolve(file)
  assert(new String(Files.readAllBytes(path)) == contents)
}

InputKey[Unit]("setLastModified") := {
  val Seq(file, lm) = Def.spaceDelimited("").parsed
  IO.setModifiedTimeOrFalse(baseDirectory.value / file, lm.toLong)
}

InputKey[Unit]("checkLastModified") := {
  val Seq(file, rawlm) = Def.spaceDelimited("").parsed
  val (not, lm) = if (rawlm.startsWith("!")) (true, rawlm.drop(1).toLong) else (false, rawlm.toLong)
  val actual = IO.getModifiedTimeOrZero(baseDirectory.value / file)
  if (not) assert(actual != lm, s"$actual == $lm for $file")
  else assert(actual == lm, s"$actual != $lm for $file")
}
