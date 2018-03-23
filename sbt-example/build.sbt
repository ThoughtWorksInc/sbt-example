sbtPlugin := true

name := "sbt-example"

organization := "com.thoughtworks.example"

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.21")

enablePlugins(Example)

organization in generateExample := "com.thoughtworks"