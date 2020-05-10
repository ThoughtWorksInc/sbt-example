package com.thoughtworks
import java.io.File

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import scala.collection.immutable
import scala.meta._
import scala.meta.contrib.{AssociatedComments, DocToken, ScaladocParser}
import scala.meta.internal.parsers.ScalametaParser
import scala.meta.internal.tokenizers.PlatformTokenizerCache
import scala.reflect.NameTransformer

/** Generates unit tests from examples in Scaladoc.
  *
  * = Getting started =
  *
  * Suppose you have some source files under `src/main/scala`,
  * which contain some code examples in their Scaladoc.
  * You can run those examples as test cases with this library.
  *
  * == Step 1: Add this plug-in in your sbt settings ==
  *
  * `<pre>
  * // project/plugins.sbt
  * addSbtPlugin("com.thoughtworks.example" % "sbt-example" % "latest.release")
  * </pre>`
  *
  * `<pre>
  * // build.sbt
  * enablePlugins(Example)
  * libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.2" % Test
  * </pre>`
  *
  * == Step 2: Run tests ==
  *
  * `<pre>
  * sbt test
  * </pre>`
  *
  * You will notice that all code blocks inside <code>{{{ }}}</code> in Scaladoc comments under `src/main/scala` are executed.
  *
  * = Common code =
  *
  * Code blocks before any Scaladoc tag are shared by all test cases. For example:
  *
  * {{{
  * import org.scalatest.freespec.AnyFreeSpec
  * }}}
  *
  * Then the name `FreeSpec` will be available for all test cases.
  *
  * @note A variable defined under a Scaladoc tag is not accessible from code blocks under another tag.
  *
  *       {{{
  *         "i" shouldNot compile
  *       }}}
  * @example A code block may define variables.
  *
  *          {{{
  *            val i = 1
  *            val s = "text"
  *          }}}
  *
  *          Those variables are accessible from other code blocks under the same Scaladoc tag.
  *
  *          {{{
  *            i should be(1)
  *          }}}
  *
  *          {{{
  *            s should be("text")
  *          }}}
  * @example A code block under a Scaladoc tag is a test case.
  *
  *          The test case is inside an [[org.scalatest.freespec.AnyFreeSpec]]
  *
  *          {{{
  *            this should be(an[AnyFreeSpec])
  *          }}}
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  * @see [[https://github.com/ThoughtWorksInc/sbt-example sbt-example on Github]]
  * @see [[autoImport]] for available sbt settings.
  */
object Example extends AutoPlugin {

  def exampleStats(source: Source, logger: Logger): Seq[Stat] = {
    val comments = AssociatedComments(source)

    def scaladocTestTree(leadingComments: Set[Token.Comment]): List[Stat] = {
      leadingComments.toList.flatMap { comment =>
        ScaladocParser.parseScaladoc(comment).toSeq.flatMap { scaladoc =>
          val (code, trailing, tags) = scaladoc.foldRight[(List[Stat], List[Stat], List[Stat])]((Nil, Nil, Nil)) {
            case (DocToken(DocToken.CodeBlock, None, Some(codeBlock)),
                  (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
              val Term.Block(stats) =
                new ScalametaParser(Input.String("{\n" + codeBlock + "\n}"), dialects.ParadiseTypelevel212).parseStat()
              (stats ++: codeAccumulator, trailingAccumulator, tagAccumulator)
            case (DocToken(tagKind: DocToken.TagKind, Some(name), Some(description)),
                  (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
              val tag = if (codeAccumulator.nonEmpty) {
                q"""
                  ${Lit.String(s"${tagKind.label} $name")}.in(try {
                    this.markup($description)
                    ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
              } else {
                q"""
                  ${Lit.String(s"${tagKind.label} $name")} - {
                    this.markup($description)
                    ..$trailingAccumulator
                  }
                """
              }
              (Nil, Nil, tag :: tagAccumulator)
            case (DocToken(tagKind: DocToken.TagKind, None, Some(body)),
                  (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
              val tag = if (codeAccumulator.nonEmpty) {
                q"""
                  ${Lit.String(s"${tagKind.label} $body")}.in(try {
                     ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
              } else {
                q"""
                  ${Lit.String(s"${tagKind.label} $body")} - {
                    ..$trailingAccumulator
                  }
                """
              }
              (Nil, Nil, tag :: tagAccumulator)
            case (DocToken(tagKind: DocToken.TagKind, name, body),
                  (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
              val tag = if (codeAccumulator.nonEmpty) {
                q"""
                  ${Lit.String(tagKind.label)}.-(try {
                     ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
              } else {
                q"""
                  ${Lit.String(tagKind.label)} - {
                    ..$trailingAccumulator
                  }
                """
              }
              (Nil, Nil, tag :: tagAccumulator)
            case (DocToken(DocToken.Paragraph, None, None), accumulators) =>
              accumulators
            case (otherToken, (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
              val tokenXml = otherToken match {
                case DocToken(DocToken.InheritDoc, None, None) =>
                  "@inheritdoc"
                case DocToken(DocToken.Paragraph, None, Some(text)) =>
                  <p>{text}</p>
                case DocToken(DocToken.Heading, None, Some(text)) =>
                  <h3>{text}</h3>
                case DocToken(DocToken.SubHeading, None, Some(text)) =>
                  <h4>{text}</h4>
                case DocToken(DocToken.Description, None, Some(text)) =>
                  if (text.startsWith("@")) {
                    logger.warn(
                      s"Invalid Scaladoc tag detected at ${comment.pos} (missing parameters for the tag?): \n\t$text")
                  }
                  text
                case _ =>
                  otherToken
              }
              val markup = q"this.markup(${tokenXml.toString})"
              if (codeAccumulator.nonEmpty) {
                (markup :: codeAccumulator, trailingAccumulator, tagAccumulator)
              } else {
                (codeAccumulator, markup :: trailingAccumulator, tagAccumulator)
              }
          }
          code ::: trailing ::: tags
        }
      }
    }

    def testTree(tree: Tree): Seq[Stat] = {
      def templateTestTree(name: Name, template: Template) = {
        import template._
        val title = name.syntax
        q"""$title - {
          ..${scaladocTestTree(comments.leading(tree))}
          ..${early.flatMap(testTree)}
          ..${stats.to[immutable.Seq].flatMap(_.flatMap(testTree))}
        }""" :: Nil
      }
      def leafTestTree(name: Name) = {
        val title = name.syntax
        val trees = scaladocTestTree(comments.leading(tree))
        if (trees.isEmpty) {
          Nil
        } else {
          q"""$title - {
              ..$trees
            }""" :: Nil
        }
      }
      tree match {
        case Pkg(termRef, children) =>
          val packageName = termRef.toString
          q"""$packageName - {
              ..${scaladocTestTree(comments.leading(tree))}
              ..${children.flatMap(testTree)}
            }""" :: Nil
        case Pkg.Object(_, name, template: Template) =>
          templateTestTree(name, template)
        case Defn.Object(_, name, template: Template) =>
          templateTestTree(name, template)
        case Defn.Trait(_, name, _, _, template: Template) =>
          templateTestTree(name, template)
        case Defn.Class(_, name, _, _, template: Template) =>
          templateTestTree(name, template)
        case Defn.Def(_, name, _, _, _, _) =>
          leafTestTree(name)
        case Defn.Type(_, name, _, _) =>
          leafTestTree(name)
        case Defn.Val(_, Seq(Pat.Var.Term(name)), _, _) =>
          leafTestTree(name)
        case Defn.Var(_, Seq(Pat.Var.Term(name)), _, _) =>
          leafTestTree(name)
        case Defn.Macro(_, name, _, _, _, _) =>
          leafTestTree(name)
        case Ctor.Secondary(_, name, _, _) =>
          leafTestTree(name)
        case _ =>
          Nil
      }
    }
    source.stats.flatMap(testTree)

  }

  override def trigger: PluginTrigger = noTrigger

  override def requires: Plugins = JvmPlugin

  /** Contains sbt setting keys, which will be automatically imported into your `build.sbt`.
    *
    * You need to manually import this [[autoImport]] object in `.scala` files, e.g. an sbt plugin, or this Scaladoc.
    *
    * {{{
    * import com.thoughtworks.Example.autoImport._
    * }}}
    */
  object autoImport {

    /** Generate unit tests from examples in Scaladoc. */
    val generateExample = taskKey[Seq[File]]("Generate unit tests from examples in Scaladoc.")

    /** Super types of the generated unit test suite class for examples in Scaladoc.
      *
      * @example The default value of this [[exampleSuperTypes]] settings are
      *          [[org.scalatest.freespec.AnyFreeSpec]] and [[org.scalatest.matchers.should.Matchers]].
      *
      *          You may want to replace [[org.scalatest.freespec.AnyFreeSpec]] to [[org.scalatest.freespec.AsyncFreeSpec]]
      *          for asynchronous tests:
      *
      *          {{{
      *          import scala.meta._
      *          exampleSuperTypes := exampleSuperTypes.value.map {
      *            case ctor"_root_.org.scalatest.freespec.AnyFreeSpec" =>
      *              ctor"_root_.org.scalatest.freespec.AsyncFreeSpec"
      *            case otherTrait =>
      *              otherTrait
      *          }
      *          }}}
      *
      *          Note that each super type can be built from a [[scala.meta.XtensionQuasiquoteCtor.ctor ctor]] quasiquote.
      *
      * @example You can introduce more ScalaTest DSL by adding more mixed-in traits
      *
      *          {{{
      *          import scala.meta._
      *          exampleSuperTypes += ctor"_root_.org.scalatest.Inside"
      *          }}}
      *
      *          Then the [[org.scalatest.Inside.inside inside]] function should be available for your Scaladoc examples.
      */
    val exampleSuperTypes =
      taskKey[List[scala.meta.Ctor.Call]](
        "Super types of the generated unit test suite class for examples in Scaladoc.")

    /** The package of the generated unit test suite class for examples in Scaladoc.
      *
      * @example The value for this [[examplePackageRef]] setting can be built from
      *          a [[scala.meta.XtensionQuasiquoteTerm.q q]] quasiquote:
      *
      *          {{{
      *          import scala.meta._
      *          examplePackageRef := q"_root_.com.yourpackagename.yourlibraryname"
      *          }}}
      */
    val examplePackageRef =
      taskKey[Term.Ref]("The package of the generated unit test suite class for examples in Scaladoc.")

    /** The class name of the generated unit test suite class for examples in Scaladoc.
      *
      * @example The value for this [[exampleClassName]] setting can be built from
      *          a [[scala.meta.XtensionQuasiquoteType.t t]] quasiquote:
      *
      *          {{{
      *          import scala.meta._
      *          exampleClassName := t"YourTestClassName"
      *          }}}
      *
      */
    val exampleClassName =
      taskKey[Type.Name]("The class name of the generated unit test suite class for examples in Scaladoc.")

    @deprecated("4.1.0", "Use `exampleClassName` instead.")
    val exampleClassRef = exampleClassName
  }
  import autoImport._

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    exampleSuperTypes := List(ctor"_root_.org.scalatest.freespec.AnyFreeSpec", ctor"_root_.org.scalatest.matchers.should.Matchers")
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    exampleClassName := {
      val splitName = name.value.split('-')
      Type.Name(NameTransformer.encode(raw"""${splitName.last}Example"""))
    },
    examplePackageRef := {
      val organizationPackageRef = new ScalametaParser(Input.String(organization.value), dialects.ParadiseTypelevel212)
        .parseRule(_.path(thisOK = false))
      val splitName = name.value.split('-')
      splitName.view(0, splitName.length - 1).foldLeft(organizationPackageRef) { (packageRef, subpackage) =>
        q"$packageRef.${Term.Name(subpackage)}"
      }

    },
    generateExample := {
      PlatformTokenizerCache.megaCache.clear()
      val outputFile = (sourceManaged in Test).value / "sbt-example-generated.scala"
      val logger = (streams in generateExample).value.log
      val content = (unmanagedSources in Compile).value.view.flatMap { file =>
        // Workaround for https://github.com/scalameta/scalameta/issues/874
        val source = new ScalametaParser(Input.File(file), dialects.ParadiseTypelevel212).parseSource()
        exampleStats(source, logger)
      }.toList
      val generatedFileTree = q"""
        package ${examplePackageRef.value} {
          final class ${exampleClassName.value} extends ..${exampleSuperTypes.value} {
            ..${content}
          }
        }
      """
      IO.write(outputFile, generatedFileTree.syntax, scala.io.Codec.UTF8.charSet)
      Seq(outputFile)
    },
    (sourceGenerators in Test) += {
      generateExample.taskValue
    }
  )
}
