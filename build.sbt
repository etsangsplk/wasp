import sbt.Keys._

import scala.util.Try

val scalaV = "2.10.7"
val gitRevision = sys.env.getOrElse("GO_REVISION", Try(Process("git rev-parse HEAD").!!.stripLineEnd).getOrElse("?")).trim.take(6)
val buildVersion = sys.env.getOrElse("GO_PIPELINE_LABEL", "1.0.0-SNAPSHOT-" + gitRevision)

lazy val waspAuthenticationSpi = (project in file("authenticator-spi"))
  .settings(
    name := "authenticator-spi",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.2.1"
    )
  )
  .settings(projectSettings: _*)

lazy val sampleWaspAuthenticator = (project in file("sample-wasp-authenticator"))
  .settings(
    name := "sample-wasp-authenticator"
  )
  .settings(projectSettings: _*)
  .dependsOn(waspAuthenticationSpi)

lazy val appDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "com.typesafe.play" %% "play" % "2.3.0" notTransitive(),
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.3",
  "com.twitter" %% "chill" % "0.7.0",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.4",
  ws
)

lazy val server = (project in file("server"))
  .settings(
    name := "server",
    libraryDependencies ++= appDependencies
  )
  .settings(projectSettings: _*)
  .dependsOn(waspAuthenticationSpi)
  .enablePlugins(play.PlayScala)
  .dependsOn(sampleWaspAuthenticator)

lazy val projectSettings = net.virtualvoid.sbt.graph.DependencyGraphPlugin.projectSettings ++ Seq(
  version := buildVersion,
  organization := "com.indix.wasp",
  scalaVersion := scalaV,
  resolvers += Resolver.mavenLocal,
  excludeDependencies ++= Seq(
    SbtExclusionRule("cglib", "cglib-nodep"),
    SbtExclusionRule("commons-beanutils", "commons-beanutils"),
    SbtExclusionRule("commons-beanutils", "commons-beanutils-core")
  ),
  parallelExecution in ThisBuild := false,
  scalacOptions ++= Seq("-unchecked",
                        "-feature"
                        // , "-Ylog-classpath" // useful while debugging dependency classpath issues
  )
)

lazy val client = (project in file("client/scala"))
  .settings(
    name := "client",
    libraryDependencies ++= base,
    crossScalaVersions := Seq("2.10.4", "2.11.8")
  )

val base = Seq(
  "org.asynchttpclient" % "async-http-client" % "2.5.3",
  "com.github.scopt" %% "scopt" % "3.2.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.3"
)

