sbtPlugin := true

name := "sbt-example"

organization := "com.thoughtworks.example"

scriptedSettings

scriptedBufferLog := false

test := scripted.toTask("").value
