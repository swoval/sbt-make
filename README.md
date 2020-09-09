### sbt-make
**It is a sad state of affairs that this feels necessary, but I ask that if you support or are planning to vote for the reelection of the 45th president of the United States of America, that you not use this plugin.**

sbt-make allows sbt build and plugin authors to write tasks that transform
project source files into output files. It extends the sbt task definition
syntax to explicitly specify the source -> target file mappings using a syntax
that is inspired by [make](https://www.gnu.org/software/make/).

### Features

* Incremental evaluation: tasks are only run when their source file
dependencies change.
* Automatic file monitoring: sbt-make interoperates with sbt's `~` command to
provide file monitoring. In continuous build mode, tasks are automatically
re-evaluated whenever dependent source files change.
* Language agnostic: sbt-make can be used to build any kind of source files, not
just scala and java files.

### Installation

sbt-make requires sbt version >= 1.3.0. Install by adding:
```
addSbtPlugin("com.swoval" % "sbt-make" % "0.1.4")
```
to `project/plugins.sbt` (or any `*.sbt` file in the `project` directory).

In a `*.sbt` build file, all required settings and definitions are automatically
imported into the default scope. To import these settings into a `*.scala` build
file, add:
```
import com.swoval.make._
```

### Examples
The syntax of sbt-make is similar to make within the constraints imposed by the
scala programming language. It can be used as a drop in replacement for make in
a simple `c` project:

```scala
"CC" := "gcc"
"LIB_DIR" := p"$baseDirectory/mylib"
"LIB_SRC_DIR" := p"${"LIB_DIR"}/src"
"LIB_INCLUDE_DIR" := p"${"LIB_SRC_DIR"}/include"
"LIB_EXT" := { if (scala.util.Properties.isMac) "dylib" else "so" }
"LINK_OPTS" := { if (scala.util.Properties.isMac) "-dynamiclib" else "-shared" }
"OBJECT_DIR" := p"$target/objects"

pat"${"OBJECT_DIR"}/%.o" :-
    (pat"${"LIB_SRC_DIR"}/%.c", p"${"LIB_INCLUDE_DIR"}/mylib.h") build
  sh(m"${"CC"} -c ${`$<`} -I${"LIB_INCLUDE_DIR"} -o ${`$@`}")

p"$target/libmylib.${"LIB_EXT"}" :- pat"${"OBJECT_DIR"}/%.o" build
  sh(m"${"CC"} ${"LINK_OPTS"} -o ${`$@`} ${`$^`}")

p"$target/main.out" :-
    (p"src/main.c", p"$target/libmylib.${"LIB_EXT"}", p"${"LIB_INCLUDE_DIR"}/mylib.h") build
  sh(m"${"CC"} -I${"LIB_INCLUDE_DIR"} ${`$<`} -L$target -lmylib -o ${`$@`}")

p"run".phony :- p"$target/main.out" build
  sh(m"${`$<`} 5")
```

To generate the main executable in the sbt shell, one would run:
```
make target/main.out
```
To run the executable:
```
make run
```
The full example along with source files and a roughly equivalent
[Makefile](https://github.com/swoval/sbt-make/blob/develop/examples/gcc/Makefile)
is available at
[gcc](https://github.com/swoval/sbt-make/tree/develop/examples/gcc).

There is also an example of how to make a simple [static site
generator](https://github.com/swoval/sbt-make/tree/develop/examples/static-site).
### Usage

A simple sbt-make task can be defined like so:

```
p"foo" :- p"bar" build Files.copy(`$<`, `$@`)
```

In this example, a path with the name `foo` depends on a source file `bar`. When
`bar` changes, it is copied into foo. To run this task in the sbt shell, one
would simply evaluate `make foo`. The `p"foo"` string interpolation syntax is
used to specify a `Path` (see [String Interpolation](#interpolation)).

#### Patterns

sbt-make tasks may be defined for files matching a pattern:
```
pat"target/%.o" :- (pat"src/%.c", p"foo.h") build sh(m"gcc -o ${`$@`} -c ${`$<`}")
```
For pattern targets, the first dependency on the right hand side _must_ also be
a pattern. A pattern task will be evaluated for each of the source files that
match the source pattern. By default, the target path for a given source file is
computed by rebasing the source file and transforming the file name by simple
substitution. In the example above, `src/foo.c` is remapped to `target/foo.o`.
To configure this mapping, see [Target pattern remapping](#remapping).

Patterns can also be used as dependencies for tasks with a single path target.
For example:
```
p"target/libmylib.dylib" :- pat"target/objects/%.o" build
  sh(s"gcc -dynamiclib -o ${`$@`} ${`$^`}")
```
When used this way, there must be a corresponding pattern task definition for
the dependent pattern (`pat"target/objects/%.o` in this example).

<a name="interpolation"></a>

<a name="automatic"></a>

#### Non-file tasks

sbt-make can also be used to generate incremental task with values that are
_not_ one or more files. For example:
```
val fooContents = taskKey[String]("Returns the contents of foo.txt")
fooContents :- p"foo.txt" build
  new String(Files.readAllBytes(`$^`))
```

The body of `fooContents` will only be evaluated when a change to `foo.txt` is
detected.

It is also possible for an incremental task to depend on another incremental
task that is not a file:
```
val barFooConcatenation =
  taskKey[String]("Concatenates the contents of foo.txt and bar.txt")
barFooConcatenation :- p"bar.txt" build
  fooContents.track + new String(Files.readAllBytes(`$^`))
```

In this example, `fooContents.track` creates an additional dependency in the
task (but it is not added to the automatic variables in `$^` since it is not a
path). The barFooConcatenation body will be evaluated if either the value of
`fooContents` or the contents of `bar.txt` change. Otherwise it will not be
re-evaluated.

In order to define a custom incremental task with return type `A`,
there must be an implicit instance of `sjsonnew.JsonFormat[A]` available. The
[sjson-new](https://github.com/eed3si9n/sjson-new) library is the json
serialization library used by sbt internally. Most basic data types like
`String`, `Integer`, `File`, etc. are available. In sbt-make, unlike sbt, it is
not necessary to import anything to bring the default json formats into scope.
However, the build may provide a custom formatter in the build and it will be
preferred over the defaults.

### Automatic variables

sbt-make provides a subset of the [automatic
variables](https://www.gnu.org/software/make/manual/html_node/Automatic-Variables.html)
present in make. To match the syntax of make, they are defined as symbolic
operators. This requires that they be wrapped in backticks in the build
definition. The available variables are:

* `$<` -- the first source dependency. When the target is a pattern, this will be
one of the source files that match the source pattern.
* `$@` -- the target path for a task. If the target is a pattern, it
is the path generated by mapping the source path to a target file. See [Target
pattern remapping](#remapping) for more details.
* `$^` -- a `Seq[Path]` for all of the source dependencies.
* `$?` -- a `Seq[Path]` of source dependencies that have changed since the
last successful run. If there are pattern dependencies, this will include
changed and modified paths but not deleted paths.

For consistency with make, the `toString` method of any the automatic variables
returning `Seq[Path]` have been rewritten to display a space separated list of
paths.

### String interpolation

Paths and patterns are central to defining sbt-make tasks, so a special syntax
is added for creating instances of `Path` and `Pattern`.

The `p` string interpolator creates instances of `Path`. If a setting key is
provided as an identifier in the interpolated string, it is expanded, i.e.
`p"$target/foo"` is loosely translated to
`Paths.get(target.value).resolve("foo")`. Similarly, the `pat` string
interpolator creates instance of `Pattern`. Finally, the `m` string interpolator
produces an output string and also will replace setting and task keys with a
call to their value.

### Shell commands

For convenience, sbt-make provides the method `sh` that can be used to run an
arbitrary shell command. It blocks the task on an external call to the provided
command. The stdin and stderr of the forked task are printed to the info and
error streams of the sbt logger.

### String variables

sbt-make allows builds to declare top level string constants as settings:
```
"base" := baseDirectory.value
```
These constants can be used inside of the custom string interpolators:
```
"objects" := p"${"base"}/objects"
```
This allows the string name of the constant to also be the reference for the
value. An equivalent approach would be:
```
val base = settingKey[String]("the base directory as string")
base := baseDirectory.value.toString
val objects = settingKey[String]("the objects directory as string")
objects := p"$base/objects".toString
// objects := (baseDirectory.value / "objects").toString also works
```

### Parallelism

sbt-make is parallel by default. This means that independent tasks will be run
in parallel assuming there are available resources. Like make, the number of
concurrent tasks can be configured with the `j` option. For example, the
command
```
make -j 1 foo
```
ensures that only one make task will be run at a time to generate the target
`foo` and all of its dependencies.

The default parallelism can be configured by setting the `makeParallelism`
setting:
```
Global / makeParallelism := 1
```
will set the default limit of concurrently running tasks to `1`. This value can
be overridden with the `-j` parameter.

### Advanced

<a name="remapping"></a>
#### Target pattern remapping
To override the source file to target file mapping, one can add an entry to
`makePatternMapping`. For example, to change the extension from `.c` to `.o` and
also prepend the output filename with `bar`, write:
```
makePatternMappings += (pat"target/%.o", pat"src/%.c") -> {
  pat"src/%.c".rebase(pat"target/%.o", sourcePath =>
      sourcePath.rename(fileName => "bar" + fileName.replaceAll(".c$", ".o"))
}
```

The `rebase` extension method on `Pattern` takes a target path and a function
from `Path => Path` and returns a function `Path => Option[Path]`. If the input
path starts with the source pattern base path (`src` in the example above), the
input function is evaluated on the input path _relativized_ with respect to the
source pattern base path. The result, is appended to the base path of the
target, which is `target` in this example and wrapped in `Some`. If the input
path does not start with the source pattern base path, the result is `None`.

### Notes

sbt-make is still experimental and the api/build syntax may change. Please feel
free to provide feedback and report any issues
[here]("https://github.com/swoval/sbt-make").
