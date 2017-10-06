/**
 * Generated by API Builder - https://www.apibuilder.io
 * Service version: 0.4.100
 * apibuilder:0.12.61 https://app.apibuilder.io/flow/delta-config/0.4.100/anorm_2_x_parsers
 */
package io.flow.delta.config.v0.anorm.conversions {

  import anorm.{Column, MetaDataItem, TypeDoesNotMatch}
  import play.api.libs.json.{JsArray, JsObject, JsValue}
  import scala.util.{Failure, Success, Try}

  /**
    * Conversions to collections of objects using JSON.
    */
  object Util {

    def parser[T](
      f: play.api.libs.json.JsValue => T
    ) = anorm.Column.nonNull { (value, meta) =>
      val MetaDataItem(columnName, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject => parseJson(f, columnName.qualified, json.getValue)
        case json: java.lang.String => parseJson(f, columnName.qualified, json)
        case _=> {
          Left(
            TypeDoesNotMatch(
              s"Column[${columnName.qualified}] error converting $value to Json. Expected instance of type[org.postgresql.util.PGobject] and not[${value.asInstanceOf[AnyRef].getClass}]"
            )
          )
        }


      }
    }

    private[this] def parseJson[T](f: play.api.libs.json.JsValue => T, columnName: String, value: String) = {
      Try {
        f(
          play.api.libs.json.Json.parse(value)
        )
      } match {
        case Success(result) => Right(result)
        case Failure(ex) => Left(
          TypeDoesNotMatch(
            s"Column[$columnName] error parsing json $value: $ex"
          )
        )
      }
    }

  }

  object Types {
    import io.flow.delta.config.v0.models.json._
    implicit val columnToSeqDeltaConfigBuildStage: Column[Seq[_root_.io.flow.delta.config.v0.models.BuildStage]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.BuildStage]] }
    implicit val columnToMapDeltaConfigBuildStage: Column[Map[String, _root_.io.flow.delta.config.v0.models.BuildStage]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.BuildStage]] }
    implicit val columnToSeqDeltaConfigInstanceType: Column[Seq[_root_.io.flow.delta.config.v0.models.InstanceType]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.InstanceType]] }
    implicit val columnToMapDeltaConfigInstanceType: Column[Map[String, _root_.io.flow.delta.config.v0.models.InstanceType]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.InstanceType]] }
    implicit val columnToSeqDeltaConfigProjectStage: Column[Seq[_root_.io.flow.delta.config.v0.models.ProjectStage]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.ProjectStage]] }
    implicit val columnToMapDeltaConfigProjectStage: Column[Map[String, _root_.io.flow.delta.config.v0.models.ProjectStage]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.ProjectStage]] }
    implicit val columnToSeqDeltaConfigBranch: Column[Seq[_root_.io.flow.delta.config.v0.models.Branch]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.Branch]] }
    implicit val columnToMapDeltaConfigBranch: Column[Map[String, _root_.io.flow.delta.config.v0.models.Branch]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.Branch]] }
    implicit val columnToSeqDeltaConfigBuild: Column[Seq[_root_.io.flow.delta.config.v0.models.Build]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.Build]] }
    implicit val columnToMapDeltaConfigBuild: Column[Map[String, _root_.io.flow.delta.config.v0.models.Build]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.Build]] }
    implicit val columnToSeqDeltaConfigConfigError: Column[Seq[_root_.io.flow.delta.config.v0.models.ConfigError]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.ConfigError]] }
    implicit val columnToMapDeltaConfigConfigError: Column[Map[String, _root_.io.flow.delta.config.v0.models.ConfigError]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.ConfigError]] }
    implicit val columnToSeqDeltaConfigConfigProject: Column[Seq[_root_.io.flow.delta.config.v0.models.ConfigProject]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.ConfigProject]] }
    implicit val columnToMapDeltaConfigConfigProject: Column[Map[String, _root_.io.flow.delta.config.v0.models.ConfigProject]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.ConfigProject]] }
    implicit val columnToSeqDeltaConfigConfig: Column[Seq[_root_.io.flow.delta.config.v0.models.Config]] = Util.parser { _.as[Seq[_root_.io.flow.delta.config.v0.models.Config]] }
    implicit val columnToMapDeltaConfigConfig: Column[Map[String, _root_.io.flow.delta.config.v0.models.Config]] = Util.parser { _.as[Map[String, _root_.io.flow.delta.config.v0.models.Config]] }
  }

  object Standard {
    implicit val columnToJsObject: Column[play.api.libs.json.JsObject] = Util.parser { _.as[play.api.libs.json.JsObject] }
    implicit val columnToSeqBoolean: Column[Seq[Boolean]] = Util.parser { _.as[Seq[Boolean]] }
    implicit val columnToMapBoolean: Column[Map[String, Boolean]] = Util.parser { _.as[Map[String, Boolean]] }
    implicit val columnToSeqDouble: Column[Seq[Double]] = Util.parser { _.as[Seq[Double]] }
    implicit val columnToMapDouble: Column[Map[String, Double]] = Util.parser { _.as[Map[String, Double]] }
    implicit val columnToSeqInt: Column[Seq[Int]] = Util.parser { _.as[Seq[Int]] }
    implicit val columnToMapInt: Column[Map[String, Int]] = Util.parser { _.as[Map[String, Int]] }
    implicit val columnToSeqLong: Column[Seq[Long]] = Util.parser { _.as[Seq[Long]] }
    implicit val columnToMapLong: Column[Map[String, Long]] = Util.parser { _.as[Map[String, Long]] }
    implicit val columnToSeqLocalDate: Column[Seq[_root_.org.joda.time.LocalDate]] = Util.parser { _.as[Seq[_root_.org.joda.time.LocalDate]] }
    implicit val columnToMapLocalDate: Column[Map[String, _root_.org.joda.time.LocalDate]] = Util.parser { _.as[Map[String, _root_.org.joda.time.LocalDate]] }
    implicit val columnToSeqDateTime: Column[Seq[_root_.org.joda.time.DateTime]] = Util.parser { _.as[Seq[_root_.org.joda.time.DateTime]] }
    implicit val columnToMapDateTime: Column[Map[String, _root_.org.joda.time.DateTime]] = Util.parser { _.as[Map[String, _root_.org.joda.time.DateTime]] }
    implicit val columnToSeqBigDecimal: Column[Seq[BigDecimal]] = Util.parser { _.as[Seq[BigDecimal]] }
    implicit val columnToMapBigDecimal: Column[Map[String, BigDecimal]] = Util.parser { _.as[Map[String, BigDecimal]] }
    implicit val columnToSeqJsObject: Column[Seq[_root_.play.api.libs.json.JsObject]] = Util.parser { _.as[Seq[_root_.play.api.libs.json.JsObject]] }
    implicit val columnToMapJsObject: Column[Map[String, _root_.play.api.libs.json.JsObject]] = Util.parser { _.as[Map[String, _root_.play.api.libs.json.JsObject]] }
    implicit val columnToSeqString: Column[Seq[String]] = Util.parser { _.as[Seq[String]] }
    implicit val columnToMapString: Column[Map[String, String]] = Util.parser { _.as[Map[String, String]] }
    implicit val columnToSeqUUID: Column[Seq[_root_.java.util.UUID]] = Util.parser { _.as[Seq[_root_.java.util.UUID]] }
    implicit val columnToMapUUID: Column[Map[String, _root_.java.util.UUID]] = Util.parser { _.as[Map[String, _root_.java.util.UUID]] }
  }

}