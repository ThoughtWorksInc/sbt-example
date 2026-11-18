enablePlugins(Example)

enablePlugins(ScalaJSPlugin)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Provided

import scala.meta._
import sbtcompat.PluginCompat._
exampleSuperTypes := Def.uncached(
  exampleSuperTypes.value :+ init"_root_.org.scalatest.Inside"
)

scalaVersion := {
  val javaVersion = sys.props("java.specification.version")
  // Keep the original compiler coverage where it can read the JDK platform classes.
  if (javaVersion == "1.8" || javaVersion.toInt < 21) "3.2.1" else "3.3.9"
}

TaskKey[Unit]("checkGenerated") := {
  val generated = (Test / sourceManaged).value / "sbt-example-generated.scala"
  assert(generated.exists, s"$generated does not exist")
}
