sbtPlugin := true

organization := "io.kamon"
name := "kamon-sbt-umbrella"

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.10")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.6")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.4")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0")