sbtPlugin := true

organization := "io.kamon"
name := "kamon-sbt-umbrella"

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.6")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.5.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.2.1")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.18")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
