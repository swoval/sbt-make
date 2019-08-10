"CC" := "gcc"
"LIB_DIR" := p"$baseDirectory/mylib"
"LIB_SRC_DIR" := p"${"LIB_DIR"}/src"
"LIB_INCLUDE_DIR" := p"${"LIB_SRC_DIR"}/include"
"LIB_EXT" := { if (scala.util.Properties.isMac) "dylib" else "so" }
"LINK_OPTS" := { if (scala.util.Properties.isMac) "-dynamiclib" else "-shared" }
"OBJECT_DIR" := p"$target/objects"

pat"${"OBJECT_DIR"}/%.o" :- (pat"${"LIB_SRC_DIR"}/%.c", p"${"LIB_INCLUDE_DIR"}/mylib.h") build
  sh(m"${"CC"} -c ${`$<`} -I${"LIB_INCLUDE_DIR"} -o ${`$@`}")

p"$target/libmylib.${"LIB_EXT"}" :- pat"${"OBJECT_DIR"}/%.o" build
  sh(m"${"CC"} ${"LINK_OPTS"} -o ${`$@`} ${`$^`}")

p"target/main.out" :- (p"src/main.c", p"$target/libmylib.${"LIB_EXT"}", p"${"LIB_INCLUDE_DIR"}/mylib.h") build
  sh(m"${"CC"} -I${"LIB_INCLUDE_DIR"} ${`$<`} -L$target -lmylib -o ${`$@`}")

p"run".phony :- p"target/main.out" build
  sh(m"${`$<`} 5")
