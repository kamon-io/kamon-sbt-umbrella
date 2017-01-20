package io.kamon.sbt.umbrella

import sbt._
import Keys._
import bintray.{Bintray, BintrayPlugin}
import bintray.BintrayKeys.{bintrayOrganization, bintrayRepository}
import com.typesafe.sbt.SbtAspectj.AspectjKeys._
import com.typesafe.sbt.SbtAspectj._
import sbtdoge.CrossPerProjectPlugin
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object KamonSbtUmbrella extends AutoPlugin {

  override def requires: Plugins      = BintrayPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = Seq(
    scalaVersion := scalaVersionSetting.value,
    crossScalaVersions := crossScalaVersionsSetting.value,
    version := versionSetting.value,
    isSnapshot := isSnapshotVersion(version.value),
    organization := "io.kamon",
    releaseCrossBuild := false,
    releaseProcess := kamonReleaseProcess.value,
    releaseSnapshotDependencies := releaseSnapshotDependenciesTask.value,
    releaseCommitMessage := releaseCommitMessageSetting.value,
    licenses += (("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
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
    bintrayOrganization := Some("kamon-io"),
    bintrayRepository := bintrayRepositorySetting.value,
    crossPaths := true,
    pomIncludeRepository := { x => false },
    publishArtifact in Test := false,
    publishMavenStyle := publishMavenStyleSetting.value,
    pomExtra := defaultPomExtra(name.value),
    publish := publishTask.value,
    resolvers += Resolver.bintrayRepo("kamon-io", "releases")
  ) ++ CrossPerProjectPlugin.projectSettings

  object autoImport {
    val aspectJ        = "org.aspectj"      % "aspectjweaver"   % "1.8.10"
    val hdrHistogram   = "org.hdrhistogram" % "HdrHistogram"    % "2.1.9"
    val slf4jApi       = "org.slf4j"        % "slf4j-api"       % "1.7.7"
    val slf4jnop       = "org.slf4j"        % "slf4j-nop"       % "1.7.7"
    val logbackClassic = "ch.qos.logback"   % "logback-classic" % "1.0.13"
    val scalatest      = "org.scalatest"    %% "scalatest"      % "3.0.1"

    def akkaDependency(moduleName: String) = Def.setting {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" => "com.typesafe.akka" %% s"akka-$moduleName" % "2.3.15"
        case "2.12"          => "com.typesafe.akka" %% s"akka-$moduleName" % "2.4.14"
      }
    }

    def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
    def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
    def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
    def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")

    val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)

    val aspectJSettings = inConfig(Aspectj)(defaultAspectjSettings) ++ aspectjDependencySettings ++ Seq(
        aspectjVersion in Aspectj := "1.8.10",
        compileOnly in Aspectj := true,
        fork in run := true,
        fork in Test := true,
        javaOptions in Test ++= (weaverOptions in Aspectj).value,
        javaOptions in run ++= (weaverOptions in Aspectj).value,
        lintProperties in Aspectj += "invalidAbsoluteTypeName = ignore"
      )
  }

  private def scalaVersionSetting = Def.setting {
    if (sbtPlugin.value) scalaVersion.value else "2.12.1"
  }

  private def crossScalaVersionsSetting = Def.setting {
    if (sbtPlugin.value) Seq(scalaVersion.value) else Seq("2.10.6", "2.11.8", "2.12.1")
  }

  private def versionSetting = Def.setting {
    val originalVersion = (version in ThisBuild).value
    if (isSnapshotVersion(originalVersion)) {
      val gitRevision = Process("git rev-parse HEAD").lines.head
      originalVersion.replace("SNAPSHOT", gitRevision)
    } else {
      originalVersion
    }
  }

  private def releaseSnapshotDependenciesTask = Def.task {
    val moduleIds = (managedClasspath in Runtime).value.flatMap(_.get(moduleID.key))
    val snapshots = moduleIds.filter(m => m.isChanging || isSnapshotVersion(m.revision))
    snapshots
  }

  private def releaseCommitMessageSetting = Def.setting {
    val currentVersion = if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value
    if(isSnapshotVersion(currentVersion))
      s"set version to $currentVersion"
    else
      s"release version $currentVersion"
  }

  private def publishTask = Def.taskDyn[Unit] {
    if (Process("git status --porcelain").lines.size > 0) {
      Def.task {
        val log = streams.value.log
        log.error("Your working directory is dirty, please commit your changes before publishing.")
      }
    } else {
      Classpaths.publishTask(publishConfiguration, deliver)
    }
  }

  private def publishMavenStyleSetting = Def.setting {
    if (sbtPlugin.value) false else publishMavenStyle.value
  }

  private def isSnapshotVersion(version: String): Boolean = {
    (version matches """(?:\d+\.)?(?:\d+\.)?(?:\d+)-[0-9a-f]{5,40}""") || (version endsWith "-SNAPSHOT")
  }

  private def bintrayRepositorySetting = Def.setting {
    if (isSnapshot.value) "snapshots"
    else if (sbtPlugin.value) Bintray.defaultSbtPluginRepository
    else "releases"
  }

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

  private def aspectjDependencySettings = Seq(
    ivyConfigurations += Aspectj,
    libraryDependencies ++= (aspectjVersion in Aspectj) { version =>
      Seq(
        "org.aspectj" % "aspectjtools"  % version % Aspectj.name,
        "org.aspectj" % "aspectjweaver" % version % Aspectj.name,
        "org.aspectj" % "aspectjrt"     % version % Aspectj.name
      )
    }.value
  )

  private def kamonReleaseProcess = Def.setting {
    val publishStep =
      if(sbtPlugin.value) releaseStepCommandAndRemaining("publish")
      else releaseStepCommandAndRemaining("+publish")

    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishStep,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  }
}
