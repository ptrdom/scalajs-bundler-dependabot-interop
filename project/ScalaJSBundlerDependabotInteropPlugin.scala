import sbt.AutoPlugin
import sbt.Compile
import io.circe._, io.circe.parser._
import sbt._
import sbt.Keys._
import sbt.Setting
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.npmDependencies

import scala.io.Source

object ScalaJSBundlerDependabotInteropPlugin extends AutoPlugin {

  override def requires = ScalaJSBundlerPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    Compile / npmDependencies ++= {
      val source = Source.fromFile(baseDirectory.value / "package.json")
      val packageJsonContents =
        try {
          source.mkString
        } finally source.close
      val json = parse(packageJsonContents)
        .getOrElse(sys.error(s"Invalid json"))
      val libraries: Seq[(String, String)] = json.hcursor
        .downField("dependencies")
        .as[JsonObject]
        .getOrElse(sys.error("Invalid json"))
        .toIterable
        .map { case (library, versionJson) =>
          (
            library,
            versionJson.as[String].getOrElse(sys.error("Invalid json"))
          )
        }
        .toSeq
      libraries
    }
  )
}
