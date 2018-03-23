addCompilerPlugin(("org.scalameta" % "paradise" % "3.0.0-M11").cross(CrossVersion.patch))

libraryDependencies += "org.scalameta" %% "scalameta" % "1.8.0"

libraryDependencies += "org.scalameta" %% "contrib" % "1.8.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % Test

organization in ThisBuild := "com.thoughtworks.example"

scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off")

crossScalaVersions := Seq("2.12.4")

name := "example"

sourceGenerators in Test += Def.task {
  val className = s"${name.value}Spec"
  val outputFile = (sourceManaged in Test).value / s"$className.scala"
  val fileNames = (unmanagedSources in Compile).value
    .map { file =>
      import scala.reflect.runtime.universe._
      Literal(Constant(file.toString))
    }
    .mkString(",")
  val fileContent =
    raw"""@_root_.com.thoughtworks.example($fileNames) class $className extends org.scalatest.FreeSpec"""
  IO.write(outputFile, fileContent, scala.io.Codec.UTF8.charSet)
  Seq(outputFile)
}.taskValue

lazy val unidoc = project
  .enablePlugins(StandaloneUnidoc, TravisUnidocTitle)
  .disablePlugins(TravisUnidocSourceUrl)
  .dependsOn(LocalRootProject)
  .settings(
    UnidocKeys.unidocProjectFilter in ScalaUnidoc in UnidocKeys.unidoc := {
      inProjects(ThisProject)
    },
    sourceGenerators in Compile += Def.task {
      for {
        sourceFile <- (unmanagedSources in Compile in LocalRootProject).value
        sourceDirectory <- (unmanagedSourceDirectories in Compile in LocalRootProject).value
        relativeFile <- sourceFile.relativeTo(sourceDirectory)
      } yield {
        val outputFile = (sourceManaged in Compile).value / relativeFile.getPath
        val paradiseSourceContent = IO.read(sourceFile, scala.io.Codec.UTF8.charSet)
        IO.write(outputFile, paradiseSourceContent.replaceAll("inline def", "def"), scala.io.Codec.UTF8.charSet)
        outputFile
      }
    }.taskValue,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1"
  )

enablePlugins(Travis)

lazy val secret = project settings (publishArtifact := false) configure { secret =>
  sys.env.get("GITHUB_PERSONAL_ACCESS_TOKEN") match {
    case Some(pat) =>
      import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
      secret.addSbtFilesFromGit("https://github.com/ThoughtWorksInc/tw-data-china-continuous-delivery-password.git",
                                new UsernamePasswordCredentialsProvider(pat, ""),
                                file("secret.sbt"))
    case None =>
      secret
  }
}
