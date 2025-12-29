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
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
//        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-kernel" % "2.13.0",
        "org.typelevel" %% "kittens" % "3.5.0",
      ),
    ),
  )

lazy val root = (project in file("."))
  .configure(withCatsEffect)
  .settings(
    name := "trip-agent",
    libraryDependencies ++= Seq(
//      "co.fs2" %% "fs2-core" % "3.12.2",
//      "co.fs2" %% "fs2-io" % "3.12.2",
//      "com.comcast" %% "ip4s-core" % "3.7.0",
//      "com.github.cb372" %% "cats-retry" % "4.0.0",
      "com.lihaoyi" %% "os-lib" % "0.11.6" % Test,
      "com.lihaoyi" %% "sourcecode" % "0.4.4",
      "com.lmax" % "disruptor" % "3.4.4" % Runtime,
//      "com.monovore" %% "decline" % "2.5.0",
//      "com.monovore" %% "decline-effect" % "2.5.0",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.13.3",
//      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.13.3",
      "dev.optics" %% "monocle-core" % "3.3.0",
      "io.circe" %% "circe-core" % "0.14.15",
      "io.circe" %% "circe-parser" % "0.14.15" % Test,
      "io.hypersistence" % "hypersistence-tsid" % "2.1.4",
      "org.apache.logging.log4j" % "log4j-core" % "2.25.3" % Runtime,
      "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.25.3" % Runtime,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.25.3" % Runtime,
      "org.camunda.bpm.model" % "camunda-bpmn-model" % "7.24.0",
      "org.business4s" %% "workflows4s-bpmn" % "0.4.2",
      "org.business4s" %% "workflows4s-core" % "0.4.2",
//      "org.typelevel" %% "log4cats-core" % "2.7.1",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1" % Test,
    ),
  )
