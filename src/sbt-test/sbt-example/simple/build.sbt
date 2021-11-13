enablePlugins(Example)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % Provided

import scala.meta._
exampleSuperTypes += ctor"_root_.org.scalatest.Inside"
