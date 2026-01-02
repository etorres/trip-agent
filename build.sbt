ThisBuild / organization := "es.eriktorr"
ThisBuild / version := "1.0.0"
ThisBuild / idePackagePrefix := Some("es.eriktorr")
Global / excludeLintKeys += idePackagePrefix

ThisBuild / scalaVersion := "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / javacOptions ++= Seq("-source", "21", "-target", "21")

Global / cancelable := true
Global / fork := true
Global / onChangedBuildSource := ReloadOnSourceChanges

addCommandAlias(
  "check",
  "; undeclaredCompileDependenciesTest; unusedCompileDependenciesTest; scalafixAll; scalafmtSbtCheck; scalafmtCheckAll",
)

lazy val warts = Warts.unsafe.filter(x => !Set(Wart.Any, Wart.DefaultArguments).contains(x))

lazy val withBaseSettings: Project => Project =
  _.settings(
    Compile / doc / sources := Seq(),
    tpolecatDevModeOptions ++= Set(
      org.typelevel.scalacoptions.ScalacOptions
        .other("-java-output-version", List("21"), _ => true),
      org.typelevel.scalacoptions.ScalacOptions.warnOption("safe-init"),
      org.typelevel.scalacoptions.ScalacOptions.privateOption("explicit-nulls"),
    ),
    Compile / compile / wartremoverErrors ++= warts,
    Test / compile / wartremoverErrors ++= warts,
    libraryDependencies ++= Seq(
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.7.0" % Test,
      "org.typelevel" %% "weaver-cats" % "0.11.3" % Test,
      "org.typelevel" %% "weaver-scalacheck" % "0.11.3" % Test,
    ),
    Test / envVars := Map(
      "SBT_TEST_ENV_VARS" -> "true",
      "TSID_NODE" -> "1",
    ),
    Test / logBuffered := false,
  )

lazy val withCatsEffect: Project => Project =
  withBaseSettings.compose(
    _.settings(
      libraryDependencies ++= Seq(
        "io.chrisdavenport" %% "cats-scalacheck" % "0.3.2" % Test,
        "org.typelevel" %% "cats-collections-core" % "0.9.10",
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-kernel" % "2.13.0",
        "org.typelevel" %% "cats-time" % "0.6.0",
        "org.typelevel" %% "kittens" % "3.5.0",
      ),
    ),
  )

lazy val root = (project in file("."))
  .configure(withCatsEffect)
  .settings(
    name := "trip-agent",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "3.12.2",
      "co.fs2" %% "fs2-io" % "3.12.2",
      "com.comcast" %% "ip4s-core" % "3.7.0",
      "com.github.cb372" %% "cats-retry" % "4.0.0",
      "com.github.pjfanning" %% "pekko-http-circe" % "3.7.0",
      "com.ibm.icu" % "icu4j" % "78.1",
      "com.lihaoyi" %% "os-lib" % "0.11.6" % Test,
      "com.lihaoyi" %% "sourcecode" % "0.4.4",
      "com.lmax" % "disruptor" % "3.4.4" % Runtime,
      "com.monovore" %% "decline" % "2.5.0",
      "com.monovore" %% "decline-effect" % "2.5.0",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.13.4",
      "dev.langchain4j" % "langchain4j" % "1.10.0",
      "dev.langchain4j" % "langchain4j-agentic" % "1.10.0-beta18",
      "dev.langchain4j" % "langchain4j-core" % "1.10.0",
      "dev.langchain4j" % "langchain4j-ollama" % "1.10.0",
      "dev.optics" %% "monocle-core" % "3.3.0",
      "io.circe" %% "circe-core" % "0.14.15",
      "io.circe" %% "circe-parser" % "0.14.15",
      "io.hypersistence" % "hypersistence-tsid" % "2.1.4",
      "org.apache.logging.log4j" % "log4j-core" % "2.25.3" % Runtime,
      "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.25.3" % Runtime,
      "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.25.3" % Runtime,
      "org.apache.pekko" %% "pekko-actor" % "1.4.0",
      "org.apache.pekko" %% "pekko-actor-typed" % "1.4.0",
      "org.apache.pekko" %% "pekko-cluster" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-cluster-sharding" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-cluster-sharding-typed" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-cluster-tools" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-cluster-typed" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-coordination" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-distributed-data" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-http" % "1.3.0",
      "org.apache.pekko" %% "pekko-http-core" % "1.3.0",
      "org.apache.pekko" %% "pekko-persistence-jdbc" % "1.2.0",
      "org.apache.pekko" %% "pekko-persistence-query" % "1.4.0",
      "org.apache.pekko" %% "pekko-persistence-typed" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-pki" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-remote" % "1.4.0" % Runtime,
      "org.apache.pekko" %% "pekko-stream" % "1.4.0",
      "org.apache.pekko" %% "pekko-stream-typed" % "1.4.0" % Runtime,
      "org.business4s" %% "workflows4s-bpmn" % "0.4.2" % Test,
      "org.business4s" %% "workflows4s-core" % "0.4.2",
      "org.business4s" %% "workflows4s-pekko" % "0.4.2",
      "org.flywaydb" % "flyway-core" % "11.20.0",
      "org.flywaydb" % "flyway-database-postgresql" % "11.20.0",
      "org.gnieh" %% "fs2-data-json" % "1.12.0",
      "org.gnieh" %% "fs2-data-json-circe" % "1.12.0",
      "org.gnieh" %% "fs2-data-text" % "1.12.0",
      "org.http4s" %% "http4s-circe" % "0.23.33",
      "org.http4s" %% "http4s-client" % "0.23.33",
      "org.http4s" %% "http4s-core" % "0.23.33",
      "org.http4s" %% "http4s-ember-client" % "0.23.33",
      "org.postgresql" % "postgresql" % "42.7.8" % Runtime,
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "org.typelevel" %% "case-insensitive" % "1.5.0",
      "org.typelevel" %% "log4cats-core" % "2.7.1",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
      "org.typelevel" %% "vault" % "3.6.0",
    ),
  )
