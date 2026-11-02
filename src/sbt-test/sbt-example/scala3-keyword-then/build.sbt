enablePlugins(Example)

enablePlugins(ScalaJSPlugin)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Provided

import scala.meta._
exampleSuperTypes += init"_root_.org.scalatest.Inside"

scalaVersion := "3.2.1"

TaskKey[Unit]("checkGenerated") := {
  val generated = (Test / sourceManaged).value / "sbt-example-generated.scala"
  assert(generated.exists, s"$generated does not exist")
}
