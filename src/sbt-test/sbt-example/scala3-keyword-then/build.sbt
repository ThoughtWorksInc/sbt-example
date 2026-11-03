enablePlugins(Example)

enablePlugins(ScalaJSPlugin)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Provided

import scala.meta._
import sbtcompat.PluginCompat._
exampleSuperTypes := Def.uncached(
  exampleSuperTypes.value :+ init"_root_.org.scalatest.Inside"
)

scalaVersion := "3.2.1"

TaskKey[Unit]("checkGenerated") := {
  val generated = (Test / sourceManaged).value / "sbt-example-generated.scala"
  assert(generated.exists, s"$generated does not exist")
}
