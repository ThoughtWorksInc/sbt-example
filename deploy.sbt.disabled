enablePlugins(SonatypeRelease)

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
