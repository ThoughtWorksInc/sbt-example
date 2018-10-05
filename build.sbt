organization in ThisBuild := "com.thoughtworks.example"

sbtPlugin := true

name := "sbt-example"

libraryDependencies += "org.scalameta" %% "contrib" % "1.8.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % Provided // For Scaladoc links

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")

enablePlugins(Example)

import scala.meta._
examplePackageRef := q"com.thoughtworks"
