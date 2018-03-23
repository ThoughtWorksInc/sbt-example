sbtPlugin := true

name := "sbt-example"

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.21")


libraryDependencies += "org.scalameta" %% "contrib" % "1.7.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")
