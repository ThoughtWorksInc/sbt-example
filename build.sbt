enablePlugins(SbtPlugin)

scriptedLaunchOpts += "-Dplugin.version=" + version.value

organization in ThisBuild := "com.thoughtworks.example"

name := "sbt-example"

libraryDependencies += "org.scalameta" %% "contrib" % "4.1.6"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.0.1"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.4" % Provided // For Scaladoc links

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")

enablePlugins(Example)

import scala.meta._
examplePackageRef := q"com.thoughtworks"
