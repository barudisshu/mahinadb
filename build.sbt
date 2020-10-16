import com.typesafe.sbt.packager.docker.Cmd

organization in ThisBuild := "com.cplier.mahina"

name := """mahinadb"""

maintainer := "Galudisu <galudisu@gmail.com>"

/* scala versions and options */
scalaVersion in ThisBuild := "2.12.8"

// dependencies versions
lazy val log4jVersion                 = "2.12.1"
lazy val scalaLoggingVersion          = "3.9.2"
lazy val chillVersion                 = "0.9.5"
lazy val slf4jVersion                 = "1.7.25"
lazy val akkaHttpVersion              = "10.2.0"
lazy val akkaVersion                  = "2.6.9"
lazy val akkaClusterManagementVersion = "1.0.8"
lazy val akkaHttpCorsVersion          = "0.4.3"
lazy val scalaJavaCompatVersion       = "0.9.1"
lazy val simulacrumVersion            = "0.13.0"
lazy val catsVersion                  = "2.1.1"
lazy val scalatestVersion             = "3.2.2"

// make version compatible with docker for publishing
ThisBuild / dynverSeparator := "-"

// This work for jdk >= 8u131
javacOptions in Universal := Seq(
  "-J-XX:+UnlockExperimentalVMOptions",
  "-J-XX:+UseCGroupMemoryLimitForHeap",
  "-J-XX:MaxRAMFraction=1",
  "-J-XshowSettings:vm"
)

// These options will be used for *all* versions.
scalacOptions in ThisBuild ++= Seq(
  "-unchecked",
  "-feature",
  "-language:_",
  "-Ypartial-unification",
  "-Xfatal-warnings",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)
resolvers ++= Seq(
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

mainClass in (Compile, run) := Some("com.cplier.mahina.Main")

enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin)

// for docker compose
dockerExposedPorts := Seq(1600, 1601, 1602, 8600, 8601, 8602, 8402)
dockerUpdateLatest := true
version in Docker := "latest"
dockerBaseImage := "openjdk:8u171-jre-alpine"
dockerRepository := Some("cplier")
daemonUser in Docker := "root"
// enable bash command
dockerCommands := dockerCommands.value.flatMap {
  case cmd @ Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
  case other                => List(other)
}

libraryDependencies ++= {
  Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % scalaJavaCompatVersion,
    // akka
    "com.typesafe.akka"             %% "akka-actor"                   % akkaVersion,
    "com.typesafe.akka"             %% "akka-slf4j"                   % akkaVersion,
    "com.typesafe.akka"             %% "akka-remote"                  % akkaVersion,
    "com.typesafe.akka"             %% "akka-stream"                  % akkaVersion,
    "com.typesafe.akka"             %% "akka-cluster"                 % akkaVersion,
    "com.typesafe.akka"             %% "akka-cluster-tools"           % akkaVersion,
    "com.typesafe.akka"             %% "akka-coordination"            % akkaVersion,
    "com.typesafe.akka"             %% "akka-discovery"               % akkaVersion,
    "com.typesafe.akka"             %% "akka-cluster-sharding"        % akkaVersion,
    "com.typesafe.akka"             %% "akka-distributed-data"        % akkaVersion,
    "com.typesafe.akka"             %% "akka-http"                    % akkaHttpVersion,
    "com.typesafe.akka"             %% "akka-http-spray-json"         % akkaHttpVersion,
    // akka management
    "com.lightbend.akka.management" %% "akka-management"              % akkaClusterManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaClusterManagementVersion,
    // misc
    "ch.megard"         %% "akka-http-cors" % akkaHttpCorsVersion,
    "com.typesafe.akka" %% "akka-http-xml"  % akkaHttpVersion,
    // log
    "org.apache.logging.log4j"   % "log4j-core"       % log4jVersion,
    "org.apache.logging.log4j"   % "log4j-api"        % log4jVersion,
    "org.apache.logging.log4j"   % "log4j-slf4j-impl" % log4jVersion,
    "com.typesafe.scala-logging" %% "scala-logging"   % scalaLoggingVersion,
    // chill
    "com.twitter" %% "chill-akka" % chillVersion,
    // test
    "com.typesafe.akka" %% "akka-testkit"        % akkaVersion      % Test,
    "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpVersion  % Test,
    "com.typesafe.akka" %% "akka-testkit"        % akkaVersion      % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion      % Test,
    "org.scalatest"     %% "scalatest"           % scalatestVersion % Test
  )
}

addCompilerPlugin("org.spire-math"  %% "kind-projector" % "0.9.7")
addCompilerPlugin("org.scalamacros" % "paradise"        % "2.1.1" cross CrossVersion.full)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
