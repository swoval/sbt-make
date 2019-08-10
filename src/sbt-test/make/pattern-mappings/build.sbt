import java.nio.file._
makePatternMappings += {
  val targetPattern = pat"$target/out/%.o"
  val source = pat"$sourceDirectory/%.c"
  (targetPattern, source) -> source.rebase(targetPattern, _.rename(f => s"foo$f.o"))
}

pat"$target/out/%.o" :- pat"$sourceDirectory/%.c" build Files.write(`$@`, Files.readAllBytes(`$<`))

InputKey[Unit]("checkContent") := {
  val Seq(file, content) = Def.spaceDelimited("").parsed
  val actual = new String(Files.readAllBytes(p"$baseDirectory/$file"))
  assert(content == actual, s"actual content '$actual' did not match expected '$content' for $file")
}
