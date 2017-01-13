package io.kamon.sbt.umbrella

import sbt._
import Keys._
import sbt.plugins.JvmPlugin
import bintray.BintrayPlugin.autoImport.bintray
import xerial.sbt.Sonatype.sonatypeSettings
import xerial.sbt.Sonatype.SonatypeKeys.sonatypeDefaultResolver

object KamonSbtUmbrella extends AutoPlugin {

  override def requires: Plugins      = JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = sonatypeSettings ++ umbrellaSettings

  private val umbrellaSettings = Seq(
    //scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
    version := versionSetting.value,
    fork in run := true,
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
    publishTo := publishToSetting.value
  )


  def isWorkingDirectoryClean: Boolean = {
    Process("git status --porcelain").lines.size == 0
  }

  def versionSetting = Def.setting {
    val originalVersion = (version in ThisBuild).value
    println("EVALUATION THE version with: " + originalVersion)
    if (isSnapshot(originalVersion)) {
      val gitRevision = Process("git rev-parse HEAD").lines.head
      println("RETURNING: " + originalVersion.replace("SNAPSHOT", gitRevision))
      originalVersion.replace("SNAPSHOT", gitRevision)
    } else {
      originalVersion
    }
  }

  def isSnapshot(version: String): Boolean = {
    (version matches """(?:\d+\.)?(?:\d+\.)?(?:\d+)-[0-9a-f]{5,40}""") || (version endsWith "-SNAPSHOT")
  }

  def publishToSetting = Def.setting {
    if(isSnapshot(version.value))
      (publishTo in bintray).value
    else
      Some(sonatypeDefaultResolver.value)
  }


}
