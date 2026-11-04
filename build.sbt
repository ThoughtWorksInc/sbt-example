enablePlugins(SbtPlugin)

scriptedLaunchOpts += "-Dplugin.version=" + version.value

ThisBuild / organization := "com.thoughtworks.example"

ThisBuild / pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => sbtVersion.value
    case _      => "2.0.0"
  }
}

name := "sbt-example"

// Exposes Def.uncached on sbt 1.x as a no-op, so the sources are shared with sbt 2.x
addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0")

libraryDependencies += "org.scalameta" %% "scalameta" % "4.17.4"

libraryDependencies ++= {
  if (scalaBinaryVersion.value == "2.12")
    Seq(
      scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided
    )
  else
    Seq.empty
}

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Provided // For Scaladoc links

scalacOptions ++= {
  if (scalaBinaryVersion.value == "2.12")
    Seq("-Ypatmat-exhaust-depth", "off")
  else
    Seq.empty
}

enablePlugins(Example)

import scala.meta._
import sbtcompat.PluginCompat._
examplePackageRef := Def.uncached(q"com.thoughtworks")
