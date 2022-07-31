enablePlugins(Example)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.13" % Provided

import scala.meta._
exampleSuperTypes += init"_root_.org.scalatest.Inside"
