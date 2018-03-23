organization in ThisBuild := "com.thoughtworks.example"

crossScalaVersions := Seq("2.12.4")

name := "example"

lazy val `sbt-example` = project

lazy val unidoc = project.enablePlugins(StandaloneUnidoc, TravisUnidocTitle)

publishArtifact := false
