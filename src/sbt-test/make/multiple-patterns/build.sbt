import java.nio.file.Files
pat"%.txt" :- pat"%.md" build Files.copy(`$<`, `$@`)

pat"%.out" :- pat"%.txt" build Files.copy(`$<`, `$@`)
