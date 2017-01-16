package io.kamon.sbt.umbrella

import sbt._
import Keys._
import sbt.plugins.JvmPlugin
import bintray.BintrayKeys.{bintray => bintrayScope}
import xerial.sbt.Sonatype.sonatypeSettings
import xerial.sbt.Sonatype.SonatypeKeys.sonatypeDefaultResolver

object KamonSbtUmbrella extends AutoPlugin {

  override def requires: Plugins      = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] =
    sonatypeSettings ++ sonatypePublishingSettings ++ bintraySettings ++ releaseSettings ++ umbrellaSettings

  object autoImport {
    val aspectJ           = "org.aspectj"               %   "aspectjweaver"         % "1.8.10"
    val hdrHistogram      = "org.hdrhistogram"          %   "HdrHistogram"          % "2.1.9"
    val slf4jApi          = "org.slf4j"                 %   "slf4j-api"             % "1.7.7"
    val slf4jnop          = "org.slf4j"                 %   "slf4j-nop"             % "1.7.7"
    val logbackClassic    = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
    val scalatest         = "org.scalatest"             %%  "scalatest"             % "3.0.1"

    def akkaDependency(moduleName: String) = Def.setting {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11"  => "com.typesafe.akka" %% s"akka-$moduleName" % "2.3.15"
        case "2.12"           => "com.typesafe.akka" %% s"akka-$moduleName" % "2.4.14"
      }
    }

    def compileScope   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
    def testScope      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
    def providedScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
    def optionalScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
  }

  private val umbrellaSettings = Seq(
    scalaVersion := scalaVersionSetting.value,
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
    version in ThisBuild := versionSetting.value,
    isSnapshot := isSnapshotVersion(version.value),
    organization := "io.kamon",
    fork in run := true,
    licenses += (("Apache V2", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-g:vars",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-Xlog-reflective-calls",
      "-Ywarn-dead-code"
    ),
    javacOptions := Seq(
      "-Xlint:-options"
    ),
    publishTo := publishToSetting.value,
    publish := publishTask.value

  )

  private def scalaVersionSetting = Def.setting {
    if(sbtPlugin.value) scalaVersion.value else "2.12.1"
  }

  def isWorkingDirectoryClean: Boolean = {
    Process("git status --porcelain").lines.size == 0
  }

  def versionSetting = Def.setting {
    val originalVersion = (version in ThisBuild).value
    if (isSnapshotVersion(version.value)) {
      val gitRevision = Process("git rev-parse HEAD").lines.head
      originalVersion.replace("SNAPSHOT", gitRevision)
    } else {
      originalVersion
    }
  }

  def isSnapshotVersion(version: String): Boolean = {
    (version matches """(?:\d+\.)?(?:\d+\.)?(?:\d+)-[0-9a-f]{5,40}""") || (version endsWith "-SNAPSHOT")
  }

  def publishToSetting = Def.setting {
    if(isSnapshotVersion((version in ThisBuild).value))
      (publishTo in bintrayScope).value
    else
      Some(sonatypeDefaultResolver.value)
  }

  def publishTask = Def.task {
    import bintray.BintrayKeys._
    import com.typesafe.sbt.pgp.PgpKeys._

    if(isSnapshotVersion((version in ThisBuild).value)) {
      Def.taskDyn {
        val ep = bintrayEnsureBintrayPackageExists.value
        val el = bintrayEnsureLicenses.value
        val _ = publish.value
        val isRelease = bintrayReleaseOnPublish.value
        if (isRelease) bintrayRelease
        else Def.task {
          val log = sLog.value
          log.warn("You must run bintrayRelease once all artifacts are staged.")}
      }
    } else publishSigned
  }


  /**
    *   Snapshot publishing to Bintray
    */

  private val bintraySettings = {
    import bintray.BintrayKeys._

    Seq(
      bintrayOrganization := Some("kamon-io")
    )
  }


  /**
    *   Sonatype Publishing Settings
    */

  private val sonatypePublishingSettings = Seq(
    crossPaths := true,
    pomIncludeRepository := { x => false },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomExtra := defaultPomExtra(name.value)
  )

  def defaultPomExtra(projectName: String) = {
    <url>http://kamon.io</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>git://github.com/kamon-io/{projectName}.git</url>
      <connection>scm:git:git@github.com:kamon-io/{projectName}.git</connection>
    </scm>
    <developers>
      <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
      <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
    </developers>
  }


  /**
    *   Release process.
    */
  private val releaseSettings = {
    import sbtrelease.ReleasePlugin.autoImport._
    import com.typesafe.sbt.pgp.PgpKeys._

    Seq(
      releaseCrossBuild := true,
      pgpSecretRing := file(System.getProperty("user.home")) / ".gnupg" / "kamon_pubring.gpg",
      pgpPublicRing := file(System.getProperty("user.home")) / ".gnupg" / "kamon_secring.gpg"
    )
  }


}
