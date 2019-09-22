TaskKey[Unit]("foo") :- p"foo.txt" build {
  println("hello")
  println(m"${"bar"}")
}

"bar" := "baz"
