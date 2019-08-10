name := "sbt-make"

organization := "com.swoval"

version := "0.1.0-SNAPSHOT"

homepage := Some(url("https://github.com/swoval/sbt-make"))
scmInfo := Some(
  ScmInfo(url("https://github.com/swoval/sbt-make"), "git@github.com:swoval/sbt-make.git")
)
developers := List(
  Developer(
    "username",
    "Ethan Atkins",
    "contact@ethanatkins.com",
    url("https://github.com/eatkins")
  )
)
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle in publishLocal := false
publishTo := {
  val p = publishTo.value
  if (sys.props.get("SonatypeSnapshot").fold(false)(_ == "true"))
    Some(Opts.resolver.sonatypeSnapshots): Option[Resolver]
  else if (sys.props.get("SonatypeStaging").fold(false)(_ == "true"))
    Some(Opts.resolver.sonatypeStaging): Option[Resolver]
  else if (sys.props.get("SonatypeRelease").fold(false)(_ == "true"))
    Some(Opts.resolver.sonatypeReleases): Option[Resolver]
  else p
}

dependencyOverrides := "org.scala-sbt" % "sbt" % "1.3.0" :: Nil
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"

ThisBuild / turbo := true

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(SbtPlugin)
