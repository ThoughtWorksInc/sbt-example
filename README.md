# sbt-example
**sbt-example** is a sbt plugin to enable [example.scala](https://javadoc.io/page/com.thoughtworks.example/unidoc_2.12/latest/com/thoughtworks/example.html).

## Usage

``` sbt
// project/plugins.sbt
addSbtPlugin("com.thoughtworks.example" % "sbt-example" % "latest.release")
```

``` sbt
// build.sbt
enablePlugins(Example)
```

``` shell
sbt test
```
