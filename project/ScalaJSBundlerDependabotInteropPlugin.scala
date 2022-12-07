import sbt.AutoPlugin
import sbt.Compile
import io.circe._
import io.circe.parser._
import sbt._
import sbt.Keys._
import sbt.Setting
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.npmDependencies
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.npmDevDependencies
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.startWebpackDevServer
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.webpack
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.webpackCliVersion

import scala.io.Source

object ScalaJSBundlerDependabotInteropPlugin extends AutoPlugin {

  override def requires = ScalaJSBundlerPlugin

  object autoImport {
    val parsedProdDependencies: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Parsed package.json dependencies"
      )
    val parsedDevDependencies: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Parsed package.json devDependencies"
      )
  }
  import autoImport._

  private sealed trait NpmDependencyType
  private object NpmDependencyType {
    case object Prod extends NpmDependencyType
    case object Dev extends NpmDependencyType
  }

  private object ScalaJSBundlerPackageJson {
    val devDependencies = Set(
      "source-map-loader",
      "webpack",
      "webpack-cli",
      "webpack-dev-server"
    )

    // following assertions check for package.json fragments that are set by scalajs-bundler and are not configurable,
    // so they must be present for package.json and package-lock.json to remain in sync.
    val assertions: Set[Json => Unit] = Set(
      json =>
        require(
          json.hcursor
            .downField("private")
            .as[Boolean]
            .contains(true),
          """package.json must contain '"private":true'"""
        ),
      json =>
        require(
          json.hcursor
            .downField("license")
            .as[String]
            .contains("UNLICENSED"),
          """package.json must contain '"license":"UNLICENSED"'"""
        ),
      json =>
        require(
          json.hcursor
            .downField("devDependencies")
            .downField("concat-with-sourcemaps")
            .as[String]
            .contains("1.0.7"),
          """package.json must contain '"devDependencies":{"concat-with-sourcemaps":"1.0.7"}'"""
        ),
      json =>
        require(
          json.hcursor
            .downField("devDependencies")
            .downField("source-map-loader")
            .as[String]
            .contains("2.0.0"),
          """package.json must contain '"devDependencies":{"source-map-loader":"2.0.0"}'"""
        )
    )
  }

  private def parseDependencies(npmDependencyType: NpmDependencyType) =
    Def.setting {
      val source = Source.fromFile(baseDirectory.value / "package.json")
      val packageJsonContents =
        try {
          source.mkString
        } finally source.close
      val json = parse(packageJsonContents)
        .getOrElse(sys.error(s"Invalid json"))
      ScalaJSBundlerPackageJson.assertions.foreach(_(json))
      json.hcursor
        .downField(npmDependencyType match {
          case NpmDependencyType.Prod => "dependencies"
          case NpmDependencyType.Dev  => "devDependencies"
        })
        .as[JsonObject]
        .getOrElse(sys.error("Invalid json"))
        .toIterable
        .map { case (library, versionJson) =>
          library -> versionJson.as[String].getOrElse(sys.error("Invalid json"))
        }
        .toMap
    }

  override lazy val projectSettings: Seq[Setting[_]] = {

    Seq(
      parsedProdDependencies := parseDependencies(NpmDependencyType.Prod).value,
      parsedDevDependencies := parseDependencies(NpmDependencyType.Dev).value,
      Compile / npmDependencies ++= parsedProdDependencies.value.toSeq,
      Compile / npmDevDependencies ++= parsedDevDependencies.value
        .filterNot({ case (name, _) =>
          ScalaJSBundlerPackageJson.devDependencies.contains(name)
        })
        .toSeq,
      webpack / version := {
        val default = (webpack / version).value
        parsedDevDependencies.value
          .getOrElse("webpack", default)
      },
      startWebpackDevServer / version := {
        val default = (startWebpackDevServer / version).value
        parsedDevDependencies.value
          .getOrElse("webpack-dev-server", default)
      },
      webpackCliVersion := {
        val default = (webpack / version).value
        parsedDevDependencies.value
          .getOrElse("webpack-cli", default)
      }
    )
  }
}
