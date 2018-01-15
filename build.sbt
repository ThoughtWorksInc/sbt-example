sbtPlugin := true

name := "sbt-example"

organization := "com.thoughtworks.example"

scriptedSettings

scriptedBufferLog := false

test := scripted.toTask("").value

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.21")
