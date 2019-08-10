import java.nio.file._
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

pat"target/patterns/%.txt" :- pat"patterns/%.md" build
  Files.copy(`$<`, `$@`, REPLACE_EXISTING)
