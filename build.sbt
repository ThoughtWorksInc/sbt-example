organization in ThisBuild := "com.thoughtworks.example"

sbtPlugin := true

name := "sbt-example"

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")

libraryDependencies += "org.scalameta" %% "contrib" % "1.7.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % Provided // For Scaladoc links

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")

enablePlugins(Example)

import scala.meta._
examplePackageRef := q"com.thoughtworks"
