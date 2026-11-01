sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.thoughtworks.example" % "sbt-example" % x)
  case _       =>
    sys.error(
      """|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
}

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0")
