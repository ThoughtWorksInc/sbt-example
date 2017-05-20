package com.thoughtworks
import scala.annotation._
import scala.collection.immutable
import scala.meta.Term.Block
import scala.meta._
import scala.meta.contrib.{AssociatedComments, DocToken, ScaladocParser}
import scala.meta.parsers.Parsed.Success

/** Generates unit tests from examples in Scaladoc in `files`.
  *
  * By adding this annotation on `YourClassSpec` like this:
  *
  * `<pre>
  * @example("YourClass.scala") class YourClassSpec
  * </pre>`
  *
  * The `YourClassSpec` will become a [[org.scalatest.FreeSpec]] according to Scaladoc comments in `YourClass.scala`.
  *
  * == Scaladoc layout conversions ==
  *
  * Code blocks before any Scaladoc tag are shared by all test cases. For example:
  *
  * {{{
  *   import org.scalatest._, Matchers._
  * }}}
  *
  * Then [[http://www.scalatest.org/user_guide/using_matchers Scalatest matchers]] will be available for all test cases.
  *
  * @example A code block under a Scaladoc tag is a test case.
  *
  *          The test case is inside a [[org.scalatest.FreeSpec]]
  *
  *          {{{
  *            this should be(a[FreeSpec])
  *          }}}
  *
  * @example A code block may define variables.
  *
  *          {{{
  *            val i = 1
  *            val s = "text"
  *          }}}
  *
  *          Those variables are accessible from other code blocks under the same Scaladoc tag
  *
  *          {{{
  *            i should be(1)
  *          }}}
  *
  *          {{{
  *            s should be("text")
  *          }}}
  *
  * @example A variable defined under a Scaladoc tag is not accessible from code blocks under another tag
  *
  *          {{{
  *            "i" shouldNot compile
  *          }}}
  *
  * @param files Source files that contain Scaladoc to import
  *
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
@compileTimeOnly("This annoation requires macro-paradise plugin")
final class example(files: String*) extends StaticAnnotation {

  // Workaround for https://github.com/scalameta/scalafmt/issues/777
  private def `inline` = ???
  private def `meta`(tree: Tree): Any = ???

  /** Returns a class definition that contains unit cases imported from Scaladoc comments.
    *
    * @note This method is the [[http://docs.scala-lang.org/sips/pending/inline-meta.html#macro-annotations inline macro]] implementation of this annotation [[example]].
    *
    *       {{{
    *         "new example().apply(???)" shouldNot compile
    *       }}}
    */
  @compileTimeOnly("This annoation requires macro-paradise plugin")
  inline def apply(defn: Any): Any = meta {
    val q"new $annoationName(..$fileAsts)" = this.asInstanceOf[Tree]
    val q"class $specClassName" = defn.asInstanceOf[Tree]
    val result = q"""class $specClassName extends _root_.org.scalatest.FreeSpec {
    ..${fileAsts.flatMap { fileAst =>
      val Lit.String(fileName) = fileAst
      // Workaround for https://github.com/scalameta/scalameta/issues/874
      val Success(source) = (Input.File(fileName), dialects.ParadiseTypelevel212).parse[Source]
      val comments = AssociatedComments(source)

      def scaladocTestTree(leadingComments: Set[Token.Comment]): List[Stat] = {
        leadingComments.toList.flatMap { comment =>
          ScaladocParser.parseScaladoc(comment).toSeq.flatMap { scaladoc =>
            val (code, trailing, tags) = scaladoc.foldRight[(List[Stat], List[Stat], List[Stat])]((Nil, Nil, Nil)) {
              case (DocToken(DocToken.CodeBlock, None, Some(codeBlock)),
                    (codeAccumulator, trailingAccumulator, tagAccumulator)) =>
                val Success(Block(stats)) = ("{" + codeBlock + "}").parse[Stat]
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
          val leadingComments = comments.leading(tree)
          if (leadingComments.isEmpty) {
            Nil
          } else {
            q"""$title - {
                ..${scaladocTestTree(leadingComments)}
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
    }}
    }"""
      println(result)
      result
  }
}
