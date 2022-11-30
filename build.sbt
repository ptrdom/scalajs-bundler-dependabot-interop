ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSBundlerDependabotInteropPlugin)
  .settings(
    name := "scalajs-bundler-dependabot-interop"
  )
