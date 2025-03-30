package com.thoughtworks
import java.io.File

import sbt.Keys._
import sbt._
import sbt.nio.Keys._
import sbt.plugins.JvmPlugin

import scala.collection.immutable
import scala.meta._
import scala.meta.contrib.{AssociatedComments, DocToken, ScaladocParser}
import scala.meta.internal.parsers.ScalametaParser
import scala.meta.internal.tokenizers.PlatformTokenizerCache
import scala.reflect.NameTransformer

/** Generates unit tests from examples in Scaladoc.
  *
  * =Getting started=
  *
  * Suppose you have some source files under `src/main/scala`, which contain
  * some code examples in their Scaladoc. You can run those examples as test
  * cases with this library.
  *
  * ==Step 1: Add this plug-in in your sbt settings==
  *
  * `<pre> // project/plugins.sbt
  *
  * addSbtPlugin("com.thoughtworks.example" % "sbt-example" % "latest.release")
  * </pre>`
  *
  * `<pre> // build.sbt
  *
  * enablePlugins(Example)
  *
  * libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % Test
  * </pre>`
  *
  * ==Step 2: Run tests==
  *
  * `<pre> sbt test </pre>`
  *
  * You will notice that all code blocks inside <code>{{{ }}}</code> in Scaladoc
  * comments under `src/main/scala` are executed.
  *
  * =Common code=
  *
  * Code blocks before any Scaladoc tag are shared by all test cases. For
  * example:
  *
  * {{{
  * import org.scalatest.freespec.AnyFreeSpec
  * }}}
  *
  * Then the name `FreeSpec` will be available for all test cases.
  *
  * @note
  *   A variable defined under a Scaladoc tag is not accessible from code blocks
  *   under another tag.
  *
  * {{{
  * "i" shouldNot compile
  * }}}
  * @example
  *   A code block may define variables.
  *
  * {{{
  * val i = 1
  * val s = "text"
  * }}}
  *
  * Those variables are accessible from other code blocks under the same
  * Scaladoc tag.
  *
  * {{{
  * i should be(1)
  * }}}
  *
  * {{{
  * s should be("text")
  * }}}
  * @example
  *   A code block under a Scaladoc tag is a test case.
  *
  * The test case is inside an [[org.scalatest.freespec.AnyFreeSpec]]
  *
  * {{{
  * this should be(an[AnyFreeSpec])
  * }}}
  * @author
  *   杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  * @see
  *   [[https://github.com/ThoughtWorksInc/sbt-example sbt-example on Github]]
  * @see
  *   [[autoImport]] for available sbt settings.
  */
object Example extends AutoPlugin {

  private def exampleStats(
      source: Source,
      logger: Logger,
      testDialect: Dialect
  ): Seq[Stat] = {
    val comments = AssociatedComments(source)

    def scaladocTestTree(leadingComments: Set[Token.Comment]): List[Stat] = {
      leadingComments.toList.flatMap { comment =>
        ScaladocParser.parseScaladoc(comment).toSeq.flatMap { scaladoc =>
          val (code, trailing, tags) = scaladoc
            .foldRight[(List[Stat], List[Stat], List[Stat])]((Nil, Nil, Nil)) {
              case (
                    DocToken(DocToken.CodeBlock, None, Some(codeBlock)),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) =>
                val Term.Block(stats) =
                  new ScalametaParser(Input.String("{\n" + codeBlock + "\n}"))(
                    testDialect
                  )
                    .parseStat()
                (stats ++: codeAccumulator, trailingAccumulator, tagAccumulator)
              case (
                    DocToken(
                      tagKind: DocToken.TagKind,
                      Some(name),
                      Some(description)
                    ),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) =>
                if (codeAccumulator.nonEmpty) {
                  val tag = q"""
                  ${Lit.String(s"${tagKind.label} $name")}.in(try {
                    this.markup($description)
                    ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
                  (Nil, Nil, tag :: tagAccumulator)
                } else {
                  (Nil, Nil, tagAccumulator)
                }
              case (
                    DocToken(tagKind: DocToken.TagKind, None, Some(body)),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) =>
                if (codeAccumulator.nonEmpty) {
                  val tag = q"""
                  ${Lit.String(s"${tagKind.label} $body")}.in(try {
                     ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
                  (Nil, Nil, tag :: tagAccumulator)
                } else {
                  (Nil, Nil, tagAccumulator)
                }
              case (
                    token @ DocToken(_: DocToken.TagKind, _, None),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) =>
                if (codeAccumulator.nonEmpty) {
                  val tag = q"""
                  ${Lit.String(token.toString)}.in(try {
                     ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
                  (Nil, Nil, tag :: tagAccumulator)
                } else {
                  (Nil, Nil, tagAccumulator)
                }
              case (
                    DocToken(DocToken.Description, None, Some(text)),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) if text.startsWith("@") =>
                logger.warn(
                  s"Invalid Scaladoc tag detected at ${comment.pos} (missing parameters for the tag?): \n\t$text"
                )
                if (codeAccumulator.nonEmpty) {
                  val tag = q"""
                  ${Lit.String(text)}.in(try {
                     ..$codeAccumulator
                  } finally {
                    ..$trailingAccumulator
                  })
                """
                  (Nil, Nil, tag :: tagAccumulator)
                } else {
                  (Nil, Nil, tagAccumulator)
                }
              case (DocToken(DocToken.Paragraph, None, None), accumulators) =>
                accumulators
              case (
                    otherToken,
                    (codeAccumulator, trailingAccumulator, tagAccumulator)
                  ) =>
                val tokenXml = otherToken match {
                  case DocToken(DocToken.InheritDoc, None, None) =>
                    "@inheritdoc"
                  case DocToken(DocToken.Paragraph, None, Some(text)) =>
                    <p>{text}</p>
                  case DocToken(DocToken.Heading1, None, Some(text)) =>
                    <h1>{text}</h1>
                  case DocToken(DocToken.Heading2, None, Some(text)) =>
                    <h2>{text}</h2>
                  case DocToken(DocToken.Heading3, None, Some(text)) =>
                    <h3>{text}</h3>
                  case DocToken(DocToken.Heading4, None, Some(text)) =>
                    <h4>{text}</h4>
                  case DocToken(DocToken.Heading5, None, Some(text)) =>
                    <h5>{text}</h5>
                  case DocToken(DocToken.Heading6, None, Some(text)) =>
                    <h6>{text}</h6>
                  case DocToken(DocToken.Description, None, Some(text)) =>
                    if (text.startsWith("@")) {
                      logger.warn(
                        s"Invalid Scaladoc tag detected at ${comment.pos} (missing parameters for the tag?): \n\t$text"
                      )
                    }
                    text
                  case _ =>
                    otherToken
                }
                val markup = q"this.markup(${tokenXml.toString})"
                if (codeAccumulator.nonEmpty) {
                  (
                    markup :: codeAccumulator,
                    trailingAccumulator,
                    tagAccumulator
                  )
                } else {
                  (
                    codeAccumulator,
                    markup :: trailingAccumulator,
                    tagAccumulator
                  )
                }
            }
          if (tags.nonEmpty || code.nonEmpty) {
            code ::: trailing ::: tags
          } else {
            Nil
          }
        }
      }
    }

    def testTree(tree: Tree): Seq[Stat] = {
      def defnTestTree(name: Name, stats: List[Stat]) = {
        val title = name.value
        val trees =
          scaladocTestTree(comments.leading(tree)) :::
            stats.flatMap(testTree)
        if (trees.isEmpty) {
          Nil
        } else {
          q"""$title - {
            ..$trees
          }""" :: Nil
        }
      }
      def templateTestTree(name: Name, template: Template) = {
        defnTestTree(name, template.early ::: template.stats)
      }
      def leafTestTree(name: Name) = {
        titledTestTree(name.value)
      }
      def titledTestTree(title: String) = {
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
          val trees =
            scaladocTestTree(comments.leading(tree)) ::: children.flatMap(
              testTree
            )
          if (trees.isEmpty) {
            Nil
          } else {
            q"""$packageName - {
              ..$trees
            }""" :: Nil
          }
        case Pkg.Object(_, name, template: Template) =>
          templateTestTree(name, template)
        case declTree: Decl =>
          declTree match {
            case declType: Decl.Type =>
              leafTestTree(declType.name)
            case declGiven: Decl.Given =>
              leafTestTree(declGiven.name)
            case declDef: Decl.Def =>
              leafTestTree(declDef.name)
            case Decl.Val(_, pats, _) =>
              titledTestTree(pats.toString())
            case Decl.Var(_, pats, _) =>
              titledTestTree(pats.toString())
          }
        case defnTree: Defn =>
          defnTree match {
            case Defn.Object(_, name, template: Template) =>
              templateTestTree(name, template)
            case trai: Defn.Trait =>
              templateTestTree(trai.name, trai.templ)
            case clazz: Defn.Class =>
              templateTestTree(clazz.name, clazz.templ)
            case de: Defn.Def =>
              leafTestTree(de.name)
            case typ: Defn.Type =>
              leafTestTree(typ.name)
            case Defn.Val(_, Seq(Pat.Var(name)), _, _) =>
              leafTestTree(name)
            case va: Defn.Var
                if va.pats.length == 1 && va.pats.head.isInstanceOf[Pat.Var] =>
              val Seq(Pat.Var(name)) = va.pats
              leafTestTree(name)
            case macr: Defn.Macro =>
              leafTestTree(macr.name)
            case group: Defn.ExtensionGroup =>
              val Some(name) =
                group.paramss.collectFirst(Function.unlift(_.collectFirst {
                  case param
                      if !param.mods.exists(Set(Mod.Implicit, Mod.Using)) =>
                    param.name
                }))
              defnTestTree(
                name,
                group.body match {
                  case Term.Block(stats) =>
                    stats
                  case stat => List(stat)
                }
              )
            case give: Defn.Given =>
              templateTestTree(give.name, give.templ)
            case alias: Defn.GivenAlias =>
              leafTestTree(alias.name)
          }
        case ctor: Ctor.Secondary =>
          leafTestTree(ctor.name)
        case _ =>
          Nil
      }
    }
    source.stats.flatMap(testTree)

  }

  /** Contains sbt setting keys, which will be automatically imported into your
    * `build.sbt`.
    *
    * You need to manually import this [[autoImport]] object in `.scala` files,
    * e.g. an sbt plugin, or this Scaladoc.
    *
    * {{{
    * import com.thoughtworks.Example.autoImport._
    * }}}
    */
  object autoImport {

    /** Generate unit tests from examples in Scaladoc. */
    val generateExample =
      taskKey[Seq[File]]("Generate unit tests from examples in Scaladoc.")

    /** Super types of the generated unit test suite class for examples in
      * Scaladoc.
      *
      * @example
      *   The default value of this [[exampleSuperTypes]] settings are
      *   [[org.scalatest.freespec.AnyFreeSpec]] and
      *   [[org.scalatest.matchers.should.Matchers]].
      *
      * You may want to replace [[org.scalatest.freespec.AnyFreeSpec]] to
      * [[org.scalatest.freespec.AsyncFreeSpec]] for asynchronous tests:
      *
      * {{{
      * import scala.meta._
      * exampleSuperTypes := exampleSuperTypes.value.map {
      *   case init"_root_.org.scalatest.freespec.AnyFreeSpec" =>
      *     init"_root_.org.scalatest.freespec.AsyncFreeSpec"
      *   case otherTrait =>
      *     otherTrait
      * }
      * }}}
      *
      * Note that each super type can be built from an `init` quasiquote.
      *
      * @example
      *   You can introduce more ScalaTest DSL by adding more mixed-in traits
      *
      * {{{
      * import scala.meta._
      * exampleSuperTypes += init"_root_.org.scalatest.Inside"
      * }}}
      *
      * Then the [[org.scalatest.Inside.inside inside]] function should be
      * available for your Scaladoc examples.
      */
    val exampleSuperTypes =
      taskKey[List[scala.meta.Init]](
        "Super types of the generated unit test suite class for examples in Scaladoc."
      )

    /** The package of the generated unit test suite class for examples in
      * Scaladoc.
      *
      * @example
      *   The value for this [[examplePackageRef]] setting can be built from a
      *   `q` quasiquote:
      *
      * {{{
      * import scala.meta._
      * examplePackageRef := q"_root_.com.yourpackagename.yourlibraryname"
      * }}}
      */
    val examplePackageRef =
      taskKey[Term.Ref](
        "The package of the generated unit test suite class for examples in Scaladoc."
      )

    /** The class name of the generated unit test suite class for examples in
      * Scaladoc.
      *
      * @example
      *   The value for this [[exampleClassName]] setting can be built from a
      *   [[scala.meta.XtensionQuasiquoteType.t t]] quasiquote:
      *
      * {{{
      * import scala.meta._
      * exampleClassName := t"YourTestClassName"
      * }}}
      */
    val exampleClassName =
      taskKey[Type.Name](
        "The class name of the generated unit test suite class for examples in Scaladoc."
      )

    @deprecated("4.1.0", "Use `exampleClassName` instead.")
    val exampleClassRef = exampleClassName

    val exampleDialect =
      taskKey[Dialect]("The source code dialect used to parse Scaladoc")
  }
  import autoImport._

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    exampleSuperTypes := List(
      init"_root_.org.scalatest.freespec.AnyFreeSpec",
      init"_root_.org.scalatest.matchers.should.Matchers"
    )
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    exampleClassName := {
      val splitName = name.value.split('-')
      Type.Name(NameTransformer.encode(raw"""${splitName.last}Example"""))
    },
    examplePackageRef := {
      val organizationPackageRef =
        new ScalametaParser(Input.String(organization.value.replace('-', '_')))(
          (Test / exampleDialect).value
        )
          .parseRule(_.path(thisOK = false))
      val splitName = name.value.split('-')
      splitName.view(0, splitName.length - 1).foldLeft(organizationPackageRef) {
        (packageRef, subpackage) =>
          q"$packageRef.${Term.Name(subpackage)}"
      }

    },
    generateExample / fileInputs := (Compile / unmanagedSources / fileInputs).value,
    generateExample := {
      val outputFile =
        (Test / sourceManaged).value / "sbt-example-generated.scala"
      val logger = (generateExample / streams).value.log
      val compileDialect = (Compile / exampleDialect).value
      val testDialect = (Test / autoImport.exampleDialect).value
      val content = generateExample.inputFiles.view.flatMap { file =>
        val source =
          new ScalametaParser(Input.File(file))(compileDialect).parseSource()
        exampleStats(source, logger, testDialect)
      }.toList
      val generatedFileTree = q"""
        package ${examplePackageRef.value} {
          final class ${exampleClassName.value} extends ..${exampleSuperTypes.value} {
            ..${content}
          }
        }
      """
      IO.write(
        outputFile,
        (testDialect, generatedFileTree).syntax,
        scala.io.Codec.UTF8.charSet
      )
      Seq(outputFile)
    },
    (Test / sourceGenerators) += {
      generateExample.taskValue
    }
  ) ++
    (for (configuration <- Seq(Test, Compile)) yield {
      configuration / exampleDialect := {
        VersionNumber((configuration / scalaVersion).value).numbers match {
          case Seq(3L, _*) =>
            dialects.Scala3
          case Seq(2L, 13L, _*) =>
            if ((configuration / scalacOptions).value.contains("-Xsource:3")) {
              dialects.Scala213Source3
            } else {
              dialects.Scala213
            }
          case Seq(2L, 12L, _*) =>
            if ((configuration / scalacOptions).value.contains("-Xsource:3")) {
              dialects.Scala212Source3
            } else {
              dialects.Scala212
            }
          case Seq(2L, 11L, _*) =>
            dialects.Scala211
          case Seq(2L, 10L, _*) =>
            dialects.Scala210
        }
      }
    })
}
