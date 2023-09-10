enablePlugins(Example)

enablePlugins(ScalaJSPlugin)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Provided

import scala.meta._
exampleSuperTypes += init"_root_.org.scalatest.Inside"

scalaVersion := "3.2.1"
