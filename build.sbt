/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.retronym.sbtxjc.SbtXjcPlugin
import play.api.libs.json._

lazy val packageData = Json.parse(scala.io.Source.fromFile("./package.json").mkString).as[JsObject]
lazy val daffodilVer = packageData("daffodilVersion").as[String]

lazy val commonSettings = {
  Seq(
    version := {
      val versionRegex = raw"""  "version": "(.*)",""".r
      val packageJsonStr = scala.io.Source.fromFile("package.json").mkString
      versionRegex.findFirstMatchIn(packageJsonStr) match {
        case Some(m) => m.group(1)
        case None    => sys.error("Missing version specifier in package.json")
      }
    },
    libraryDependencies ++= Seq(
      "org.apache.daffodil" %% "daffodil-sapi" % daffodilVer,
      "org.apache.daffodil" %% "daffodil-runtime1" % daffodilVer
    ),
    dependencyOverrides ++= Seq(
      "org.apache.commons" % "commons-lang3" % "3.12.0"
    ),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    organization := "org.apache.daffodil",
    scalaVersion := "2.12.15",
    scalacOptions ++= Seq("-Ypartial-unification"),
    // remove the -Xcheckinit option added by the sbt tpoletcat plugin. This
    // option leads to non-reproducible builds
    scalacOptions --= Seq("-Xcheckinit"),
    startYear := Some(2021)
  )
}

lazy val ratSettings = Seq(
  ratLicenses := Seq(
    ("MIT  ", Rat.MIT_LICENSE_NAME, Rat.MIT_LICENSE_TEXT_MICROSOFT),
    ("CC0  ", Rat.CREATIVE_COMMONS_LICENSE_NAME, Rat.CREATIVE_COMMONS_LICENSE_TEXT)
  ),
  ratLicenseFamilies := Seq(
    Rat.MIT_LICENSE_NAME,
    Rat.CREATIVE_COMMONS_LICENSE_NAME
  ),
  ratExcludes := Rat.excludes,
  ratFailBinaries := true
)

lazy val commonPlugins = Seq(BuildInfoPlugin, JavaAppPackaging, UniversalPlugin)

lazy val `daffodil-debugger` = project
  .in(file("."))
  .settings(commonSettings, ratSettings)
  .settings(publish / skip := true)
  .dependsOn(core)
  .aggregate(core)

lazy val core = project
  .in(file("server/core"))
  .settings(commonSettings)
  .settings(
    name := "daffodil-debugger",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.microsoft.java" % "com.microsoft.java.debug.core" % "0.35.0",
      "co.fs2" %% "fs2-io" % "3.0.6",
      "com.monovore" %% "decline-effect" % "2.2.0",
      "org.typelevel" %% "log4cats-slf4j" % "2.1.1"
    ),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, "daffodilVersion" -> daffodilVer),
    buildInfoPackage := "org.apache.daffodil.debugger.dap",
    packageName := s"${name.value}-$daffodilVer"
  )
  .enablePlugins(commonPlugins: _*)
  .dependsOn(sbtXjcProject)
  .aggregate(sbtXjcProject)

lazy val sbtXjcProject = project
  .in(file("server/sbtXjc"))
  .enablePlugins(SbtXjcPlugin)
  .settings(
    name := "daffodil-xjc",
    libraryDependencies ++= Seq(
      "javax.activation" % "activation" % "1.1.1",
      "com.sun.xml.bind" % "jaxb-xjc" % "2.1.6",
      // "org.relaxng" % "jing" % "20220510" % "runtime",

      // "com.sun.istack" % "istack-commons-tools" % "4.1.1"
    ),
    xjcCommandLine += "-nv",
    xjcCommandLine += "-p",
    xjcCommandLine += "org.apache.daffodil.tdml",
    xjcBindings += "bindings.xjb",
    xjcJvmOpts += "-classpath",
    xjcJvmOpts += s"${csrCacheDirectory.value}/https/repo1.maven.org/maven2/javax/activation/activation/1.1.1/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/javax/activation/javax.activation-api/1.2.0/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/org/glassfish/jaxb/txw2/2.2.11/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/relaxngDatatype/relaxngDatatype/20020414/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/com/sun/xsom/xsom/20140925/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/com/sun/xml/bind/external/rngom/2.2.11/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/2.21/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/org/glassfish/jaxb/codemodel/2.2.11/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-core/2.2.11/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.1/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/com/sun/istack/istack-commons-tools/2.21/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-xjc/2.2.11/*:${csrCacheDirectory.value}/https/repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.2.11/*",
    Compile / xjc / sources := Seq(file("resources/xsd"))
  )
