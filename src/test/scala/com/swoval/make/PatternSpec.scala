package com.swoval.make

import java.nio.file.Path

import com.swoval.make.MakePlugin.autoImport._
import org.scalatest.WordSpec
import sbt.io.IO

class PatternSpec extends WordSpec {
  "stem" should {
    "work with suffix" when {
      "base exists" in IO.withTemporaryDirectory { dirFile =>
        val dir = dirFile.toPath
        val pattern = pat"$dir/%.c"
        val cFile = p"$dir/foo.c"
        val nestedCFile = p"$dir/subdir/bar.c"
        assert(pattern.stem(cFile).contains(p"foo"))
        assert(pattern.stem(nestedCFile).contains(p"subdir/bar"))
        val ccFile = p"$dir/foo.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
      "no base exists" in {
        val pattern = pat"%.c"
        val cFile = p"foo.c"
        val nestedCFile = p"subdir/bar.c"
        assert(pattern.stem(cFile).contains(p"foo"))
        assert(pattern.stem(nestedCFile).contains(p"subdir/bar"))
        val ccFile = p"foo.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
    }
    "work with prefix" when {
      "base exists" in IO.withTemporaryDirectory { dirFile =>
        val dir = dirFile.toPath
        val pattern = pat"$dir/foo%"
        val cFile = p"$dir/foo.c"
        val nestedCFile = p"$dir/foo/bar.c"
        assert(pattern.stem(cFile).contains(p".c"))
        assert(pattern.stem(nestedCFile).contains(p"/bar.c"))
        val ccFile = p"$dir/fob.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
      "no base exists" in {
        val pattern = pat"foo%"
        val cFile = p"foo.c"
        val nestedCFile = p"foo/bar.c"
        assert(pattern.stem(cFile).contains(p".c"))
        assert(pattern.stem(nestedCFile).contains(p"/bar.c"))
        val ccFile = p"fob.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
    }
    "work with prefix and suffix" when {
      "base exists" in IO.withTemporaryDirectory { dirFile =>
        val dir = dirFile.toPath
        val pattern = pat"$dir/foo%.c"
        val cFile = p"$dir/foo.c"
        val nestedCFile = p"$dir/foo/bar.c"
        assert(pattern.stem(cFile).contains(p""))
        assert(pattern.stem(nestedCFile).contains(p"/bar"))
        val ccFile = p"$dir/fob.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
      "no base exists" in {
        val pattern = pat"foo%.c"
        val cFile = p"foo.c"
        val nestedCFile = p"foo/bar.c"
        assert(pattern.stem(cFile).contains(p""))
        assert(pattern.stem(nestedCFile).contains(p"/bar"))
        val ccFile = p"fob.cc"
        assert(pattern.stem(ccFile).isEmpty)
      }
    }
  }
  "rebase" should {
    "work with compatible patterns" in IO.withTemporaryDirectory { dir =>
      val outDir = p"$dir/out"
      val source = pat"$dir/%.c"
      val target = pat"$outDir/%.o"
      val rebase = source.rebase(target)
      val cFile = p"$dir/bar.c"
      assert(rebase(cFile).contains(p"$outDir/bar.o"))

      val nestedCFile = p"$dir/foo/bar/baz/buzz.c"
      assert(rebase(nestedCFile).contains(p"$outDir/foo/bar/baz/buzz.o"))
    }
    "handle patterns with neither prefix nor suffix" in IO.withTemporaryDirectory { dir =>
      val source = pat"$dir/foo/%"
      val target = pat"$dir/dest/%"
      val rebase = source.rebase(target)
      assert(rebase(p"$dir/foo/bar/baz/buzz.c").contains(p"$dir/dest/bar/baz/buzz.c"))
    }
    "throw on incompatible patterns" in IO.withTemporaryDirectory { dir =>
      val source = pat"$dir/foo%.c"
      val target = pat"$dir/%.c"
      intercept[IllegalArgumentException](source.rebase(target))
    }
    "work with custom transform" in IO.withTemporaryDirectory { dir =>
      val markdownToHtml: Path => Path = mdName => {
        def transform(s: String): String = s.replace(' ', '-').toLowerCase + ".html"
        val htmlName = mdName.getFileName.toString.split(" - ") match {
          case Array(_, suffix) => transform(suffix)
          case Array(suffix)    => transform(suffix)
        }
        Option(mdName.getParent) map (_.resolve(htmlName)) getOrElse p"$htmlName"
      }
      val source = pat"$dir/%.md"
      val target = pat"$dir/html/%.html"
      val rebase = source.rebase(target, markdownToHtml)
      val mdFile = p"$dir/post/01 - My first post.md"
      assert(rebase(mdFile).contains(p"$dir/html/post/my-first-post.html"))
      val otherMDFile = p"$dir/other-post.md"
      assert(rebase(otherMDFile).contains(p"$dir/html/other-post.html"))
    }
  }
}
