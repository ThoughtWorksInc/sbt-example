package com.thoughtworks.sbtexample

import sbt._
import Keys._
import org.scalajs.sbtplugin.{ScalaJSCrossVersion, ScalaJSPlugin}
import sbt.plugins.JvmPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.impl.ScalaJSGroupID

object Example extends AutoPlugin {

  override def trigger: PluginTrigger = noTrigger

  override def requires: Plugins = JvmPlugin

  object autoImport {
    val generateExample = taskKey[Seq[File]]("Generate unit tests from examples in Scaladoc.")
    val exampleSuperTypes =
      taskKey[Seq[String]]("Super types of the generated unit suite class for examples in Scaladoc.")
  }
  import autoImport._

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    exampleSuperTypes := Seq("_root_.org.scalatest.FreeSpec", "_root_.org.scalatest.Matchers")
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    addCompilerPlugin(("org.scalameta" % "paradise" % "3.0.0-M8").cross(CrossVersion.patch)),
    libraryDependencies += "com.thoughtworks.example" %% "example" % "1.0.3" % Test,
    libraryDependencies += {
      if (ScalaJSPlugin.AutoImport.isScalaJSProject.?.value.getOrElse(false)) {
        "org.scalatest" % "scalatest" % "3.0.3" % Test cross ScalaJSCrossVersion.binary
      } else {
        "org.scalatest" %% "scalatest" % "3.0.3" % Test
      }
    },
    name in generateExample := raw"""${(name in generateExample).value}ScaladocExample""",
    generateExample := {
      val className = s"${name.value}Spec"
      val outputFile = (sourceManaged in Test).value / raw"""${(name in generateExample).value}.scala"""
      val fileNames = (unmanagedSources in Compile).value
        .map { file =>
          import scala.reflect.runtime.universe._
          Literal(Constant(file.toString))
        }
        .mkString(",")
      val fileContent = raw"""
        package ${(organization in generateExample).value};
        @_root_.com.thoughtworks.example($fileNames)
        final class `${(name in generateExample).value}` extends ${exampleSuperTypes.value.mkString(" with ")}
      """
      IO.write(outputFile, fileContent, scala.io.Codec.UTF8.charSet)
      Seq(outputFile)
    },
    (sourceGenerators in Test) += generateExample.taskValue
  )
}
