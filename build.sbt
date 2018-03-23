organization in ThisBuild := "com.thoughtworks.example"

crossScalaVersions := Seq("2.12.4")

name := "example"

lazy val `sbt-example` = project

lazy val unidoc = project
  .enablePlugins(StandaloneUnidoc, TravisUnidocTitle)
  .disablePlugins(TravisUnidocSourceUrl)
  .dependsOn(LocalRootProject)
  .settings(
    unidocProjectFilter in ScalaUnidoc in BaseUnidocPlugin.autoImport.unidoc := {
      inProjects(ThisProject)
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1"
  )

// TODO: This project should be created by an sbt plugin.
lazy val secret = project.settings(publishArtifact := false).in {
  val secretDirectory = file(sourcecode.File()).getParentFile / "secret"
  sys.env.get("GITHUB_PERSONAL_ACCESS_TOKEN").foreach { githubPersonalAccessToken =>
    IO.delete(secretDirectory)
    org.eclipse.jgit.api.Git
      .cloneRepository()
      .setURI("https://github.com/ThoughtWorksInc/tw-data-china-continuous-delivery-password.git")
      .setDirectory(secretDirectory)
      .setCredentialsProvider(
        new org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(githubPersonalAccessToken, "")
      )
      .call()
      .close()
  }
  secretDirectory
}
