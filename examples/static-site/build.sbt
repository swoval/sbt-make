// adapted from http://www.lihaoyi.com/post/HowtocreateBuildPipelinesinScala.html

import java.nio.file._
import scalatags.Text.all.{ target => _, _ }
import scala.collection.JavaConverters._
import scala.io.Source

val blogName = taskKey[String]("The name of the blog")
blogName := "Ethan's blog"

val bootstrapURL = taskKey[URL]("The url for the bootstrap css file")
bootstrapURL := new URL("https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css")

p"target/bootstrap.min.css" build {
  // The call to .track rather than .value makes bootstrapURL a dependency of the task. The
  // task will only rerun if the value of bootstrapURL changes or the target file doesn't exist.
  val url = bootstrapURL.track
  streams.value.log.info(s"Downloading bootstrap url from $url")
  val source = Source.fromURL(url)
  try Files.write(`$@`, source.mkString.getBytes)
  finally source.close()
}

def mdNameToHtml(fileName: String) =
  s"${fileName.split(" - ").last.replace(' ', '-').toLowerCase.stripSuffix(".md")}.html"

// Normally we wouldn't need to remap the target name and the plugin would automatically
// rebase the source file into the target directory and change the extension, but the name of the
// post is used as a a page header so we support space characters in the source file name which
// need to be removed from the target name.
makePatternMappings += MakePattern.rebase(pat"target/posts/%.html", pat"posts/%.md") {
  (target, source) =>
    source.rebase(target, _.rename(mdNameToHtml))
}

def renderMarkdown(content: String): String = {
  val document = org.commonmark.parser.Parser.builder().build().parse(content)
  org.commonmark.html.HtmlRenderer.builder().build().render(document)
}

pat"target/posts/%.html" :- (pat"posts/%.md", p"target/bootstrap.min.css") build {
  val base = baseDirectory.value.toPath
  streams.value.log.info(s"Rebuilding ${base.relativize(`$@`)} from ${base.relativize(`$<`)}")
  Files.write(
    `$@`,
    html(
      head(head(link(rel := "stylesheet", href := "../bootstrap.min.css"))),
      body(
        h1(a(blogName.track, href := "../index.html")),
        h1(`$<`.getFileName.toString.stripSuffix(".md").split(" - ").last),
        raw(renderMarkdown(new String(Files.readAllBytes(`$<`))))
      )
    ).render.getBytes
  )
}

def getPreview(path: Path): String = {
  val stream = Files.lines(path)
  try renderMarkdown(stream.iterator.asScala.takeWhile(_.nonEmpty).mkString("\n"))
  finally stream.close()
}
p"target/index.html" :- (pat"posts/%.md", p"target/bootstrap.min.css") build {
  streams.value.log.info(s"Rebuilding target/index.html")
  Files.write(
    `$@`,
    html(
      head(head(link(rel := "stylesheet", href := "bootstrap.min.css"))),
      body(
        h1(blogName.track),
        `$^`.sorted.collect {
          case p if p.getFileName.toString.endsWith(".md") =>
            val postName = p.getFileName.toString.stripSuffix(".md")
            h2(
              a(postName, href := s"posts/${mdNameToHtml(postName)}"),
              raw(getPreview(p))
            )
        }
      )
    ).render.getBytes
  )
}

val all = taskKey[Seq[Path]]("All of the generated files")
all :- (pat"target/posts/%.html", p"target/index.html", p"target/bootstrap.min.css") build `$^`

val installDirectory = settingKey[Path]("The path to install the website files")
installDirectory := p"$baseDirectory/out"

val install = taskKey[Seq[Path]]("Install the generated website.")
install := {
  val files = all.value
  val rebase = pat"$target/%".rebase(pat"$installDirectory/%")
  IO.copy(files.flatMap(t => rebase(t).map(c => t.toFile -> c.toFile))).map(_.toPath).toSeq.sorted
}
