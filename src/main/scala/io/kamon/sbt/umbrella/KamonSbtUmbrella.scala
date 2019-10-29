package io.kamon.sbt.umbrella

import java.util.Calendar

import sbt.{Def, _}
import Keys._
import bintray.{Bintray, BintrayPlugin}
import bintray.BintrayKeys.{bintrayOrganization, bintrayRepository}
import sbtrelease.ReleasePlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

import scala.sys.process.Process
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import de.heikoseeberger.sbtheader.{HeaderPlugin, License}
import sbt.plugins.JvmPlugin

object KamonSbtUmbrella extends AutoPlugin {

  object autoImport {
    val kanelaAgent    = "io.kamon"         %  "kanela-agent"    % "1.0.2"
    val hdrHistogram   = "org.hdrhistogram" %  "HdrHistogram"    % "2.1.10"
    val slf4jApi       = "org.slf4j"        %  "slf4j-api"       % "1.7.25"
    val slf4jnop       = "org.slf4j"        %  "slf4j-nop"       % "1.7.24"
    val logbackClassic = "ch.qos.logback"   %  "logback-classic" % "1.2.3"
    val scalatest      = "org.scalatest"    %% "scalatest"       % "3.0.8"

    val kanelaAgentVersion = settingKey[String]("Kanela Agent version")
    val kanelaAgentJar = taskKey[File]("Kanela Agent jar")

    val noPublishing = Seq(
      publish := {},
      publishLocal := {},
      publishArtifact := false
    )

    val instrumentationSettings = Seq(
      javaAgents := Seq("io.kamon" % "kanela-agent" % kanelaAgentVersion.value % "runtime;test")
    )

    // More info here: https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Note+about+sbt-release
    val crossBuildingRootSettings = Seq(
      crossScalaVersions := Nil,
      publish / skip := true,
      releaseCrossBuild := false
    )

    def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
    def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
    def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
    def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
  }

  override def requires: Plugins      = BintrayPlugin && JvmPlugin && HeaderPlugin
  override def trigger: PluginTrigger = allRequirements

  import autoImport._
  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = Seq(
    scalaVersion := scalaVersionSetting.value,
    crossScalaVersions := crossScalaVersionsSetting.value,
    version := versionSetting.value,
    isSnapshot := isSnapshotVersion(version.value),
    organization := "io.kamon",
    organizationName := "The Kamon Project",
    organizationHomepage := Some(url("https://kamon.io/")),
    startYear := Some(2013),
    headerLicense := licenseTemplate(startYear.value),
    licenses += (("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    releaseCrossBuild := false,
    releaseProcess := kamonReleaseProcess.value,
    releaseSnapshotDependencies := releaseSnapshotDependenciesTask.value,
    releaseCommitMessage := releaseCommitMessageSetting.value,
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
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false,
    publishMavenStyle := publishMavenStyleSetting.value,
    pomExtra := defaultPomExtra(name.value),
    publish := publishTask.value,
    resolvers += Resolver.bintrayRepo("kamon-io", "releases"),
    kanelaAgentVersion := "1.0.2",
    kanelaAgentJar := findKanelaAgentJar.value
  )

  private def licenseTemplate(startYear: Option[Int]) = {
    val fromYear = startYear.getOrElse(2013)
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    Some(License.Custom(
      s"""
        | ==========================================================================================
        | Copyright © $fromYear-$thisYear The Kamon Project <https://kamon.io/>
        |
        | Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
        | except in compliance with the License. You may obtain a copy of the License at
        |
        |     http://www.apache.org/licenses/LICENSE-2.0
        |
        | Unless required by applicable law or agreed to in writing, software distributed under the
        | License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
        | either express or implied. See the License for the specific language governing permissions
        | and limitations under the License.
        | ==========================================================================================
      """.trim().stripMargin
    ))
  }

  private def scalaVersionSetting = Def.setting {
    if (sbtPlugin.value) scalaVersion.value else "2.13.0"
  }

  private def crossScalaVersionsSetting = Def.setting {
    if (sbtPlugin.value) Seq(scalaVersion.value) else Seq("2.11.12", "2.12.8", "2.13.0")
  }

  def findKanelaAgentJar = Def.task {
    update.value.matching {
      moduleFilter(organization = "io.kamon", name = "kanela-agent") &&
        artifactFilter(`type` = "jar")
    }.head
  }

  private def versionSetting = Def.setting {
    val originalVersion = (version in ThisBuild).value
    if (isSnapshotVersion(originalVersion)) {
      val gitRevision = Process("git rev-parse HEAD").lineStream.head
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
    if (Process("git status --porcelain").lineStream.size > 0) {
      Def.task {
        val log = streams.value.log
        log.error("Your working directory is dirty, please commit your changes before publishing.")
      }
    } else {
      Classpaths.publishTask(publishConfiguration)
    }
  }

  private def publishMavenStyleSetting = Def.setting {
    if (sbtPlugin.value) false else publishMavenStyle.value
  }

  private def isSnapshotVersion(version: String): Boolean = {
    (version matches """(?:\d+\.)?(?:\d+\.)?(?:\d+)(?:-[A-Z0-9]*)?-[0-9a-f]{5,40}""") || (version endsWith "-SNAPSHOT")
  }

  private def bintrayRepositorySetting = Def.setting {
    if (isSnapshot.value) "snapshots"
    else if (sbtPlugin.value) Bintray.defaultSbtPluginRepository
    else "releases"
  }

  private def defaultPomExtra(projectName: String) = {
    <url>http://kamon.io</url>
    <scm>
      <url>git://github.com/kamon-io/{projectName}.git</url>
      <connection>scm:git:git@github.com:kamon-io/{projectName}.git</connection>
    </scm>
    <developers>
      <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
      <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
    </developers>
  }

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
