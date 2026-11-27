enablePlugins(Example)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Provided

TaskKey[Unit]("checkGenerated") := {
  val generated = (Test / sourceManaged).value / "sbt-example-generated.scala"
  assert(generated.exists, s"$generated does not exist")
}
