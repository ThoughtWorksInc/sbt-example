enablePlugins(SbtPlugin)

scriptedLaunchOpts += "-Dplugin.version=" + version.value

organization in ThisBuild := "com.thoughtworks.example"

name := "sbt-example"

libraryDependencies += "org.scalameta" %% "scalameta" % "4.4.35"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.11" % Provided // For Scaladoc links

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")

enablePlugins(Example)

import scala.meta._
examplePackageRef := q"com.thoughtworks"
