/**
 * Generated by API Builder - https://www.apibuilder.io
 * Service version: 0.6.7
 * apibuilder 0.14.3 app.apibuilder.io/flow/delta-config/0.6.7/play_2_x_json
 */
package io.flow.delta.config.v0.models {

  sealed trait Config extends _root_.scala.Product with _root_.scala.Serializable

  /**
   * Defines the valid discriminator values for the type Config
   */
  sealed trait ConfigDiscriminator extends _root_.scala.Product with _root_.scala.Serializable

  object ConfigDiscriminator {

    case object ConfigProject extends ConfigDiscriminator { override def toString = "config_project" }
    case object ConfigError extends ConfigDiscriminator { override def toString = "config_error" }

    case class UNDEFINED(override val toString: String) extends ConfigDiscriminator

    val all: scala.List[ConfigDiscriminator] = scala.List(ConfigProject, ConfigError)

    private[this] val byName: Map[String, ConfigDiscriminator] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): ConfigDiscriminator = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[ConfigDiscriminator] = byName.get(value.toLowerCase)

  }

  /**
   * The name of the branch that we are actively monitoring, including any
   * information needed for the initial deploy.
   */
  case class Branch(
    name: String
  )

  /**
   * @param initialNumberInstances When first deploying this branch, the number of instances we create
   * @param memory The number of MiB of memory to set for jvm xmx
   * @param portContainer The port number on the container that is bound to the user-specified or
   *        automatically assigned host port.
   * @param portHost The port number on the container instance to reserve for your container
   * @param dependencies The names of other builds that this one is dependent on. If specified, we will
   *        ensure that we never scale this build to a tag that is ahead of the minimum
   *        version of the dependent application running in production.
   * @param version The version of Delta to use for deployments. Defaults to 1.0 if not specified
   * @param healthcheckUrl The URL used for healthchecks by the ELB
   */
  case class Build(
    name: String,
    dockerfile: String,
    initialNumberInstances: Long,
    instanceType: io.flow.delta.config.v0.models.InstanceType,
    memory: _root_.scala.Option[Long] = None,
    portContainer: Int,
    portHost: Int,
    stages: Seq[io.flow.delta.config.v0.models.BuildStage],
    dependencies: Seq[String],
    version: _root_.scala.Option[String] = None,
    healthcheckUrl: _root_.scala.Option[String] = None
  )

  /**
   * Used to indicate that there was a problem parsing the project configuration
   */
  case class ConfigError(
    errors: Seq[String]
  ) extends Config

  /**
   * Top level configuration for a project, including what builds and branches are
   * covered and the current status (e.g. enabled, paused, etc.)
   */
  case class ConfigProject(
    stages: Seq[io.flow.delta.config.v0.models.ProjectStage],
    builds: Seq[io.flow.delta.config.v0.models.Build],
    branches: Seq[io.flow.delta.config.v0.models.Branch]
  ) extends Config

  /**
   * Provides future compatibility in clients - in the future, when a type is added
   * to the union Config, it will need to be handled in the client code. This
   * implementation will deserialize these future types as an instance of this class.
   * 
   * @param description Information about the type that we received that is undefined in this version of
   *        the client.
   */
  case class ConfigUndefinedType(
    description: String
  ) extends Config

  /**
   * Represents the individual stages of the continuous delivery system that can be
   * enabled / disabled at the build level
   */
  sealed trait BuildStage extends _root_.scala.Product with _root_.scala.Serializable

  object BuildStage {

    case object SetDesiredState extends BuildStage { override def toString = "set_desired_state" }
    case object SyncDockerImage extends BuildStage { override def toString = "sync_docker_image" }
    case object BuildDockerImage extends BuildStage { override def toString = "build_docker_image" }
    case object Scale extends BuildStage { override def toString = "scale" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    case class UNDEFINED(override val toString: String) extends BuildStage

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[BuildStage] = scala.List(SetDesiredState, SyncDockerImage, BuildDockerImage, Scale)

    private[this]
    val byName: Map[String, BuildStage] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): BuildStage = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[BuildStage] = byName.get(value.toLowerCase)

  }

  /**
   * List of supported AWS instance types - see
   * https://aws.amazon.com/ec2/instance-types/
   */
  sealed trait InstanceType extends _root_.scala.Product with _root_.scala.Serializable

  object InstanceType {

    case object C4Large extends InstanceType { override def toString = "c4.large" }
    case object C4Xlarge extends InstanceType { override def toString = "c4.xlarge" }
    case object C42xlarge extends InstanceType { override def toString = "c4.2xlarge" }
    case object M4Large extends InstanceType { override def toString = "m4.large" }
    case object M4Xlarge extends InstanceType { override def toString = "m4.xlarge" }
    case object M42xlarge extends InstanceType { override def toString = "m4.2xlarge" }
    case object M5Large extends InstanceType { override def toString = "m5.large" }
    case object M5Xlarge extends InstanceType { override def toString = "m5.xlarge" }
    case object M52xlarge extends InstanceType { override def toString = "m5.2xlarge" }
    case object T2Micro extends InstanceType { override def toString = "t2.micro" }
    case object T2Small extends InstanceType { override def toString = "t2.small" }
    case object T2Medium extends InstanceType { override def toString = "t2.medium" }
    case object T2Large extends InstanceType { override def toString = "t2.large" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    case class UNDEFINED(override val toString: String) extends InstanceType

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[InstanceType] = scala.List(C4Large, C4Xlarge, C42xlarge, M4Large, M4Xlarge, M42xlarge, M5Large, M5Xlarge, M52xlarge, T2Micro, T2Small, T2Medium, T2Large)

    private[this]
    val byName: Map[String, InstanceType] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): InstanceType = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[InstanceType] = byName.get(value.toLowerCase)

  }

  /**
   * Represents the individual stages of the continuous delivery system that can be
   * enabled / disabled at the project level
   */
  sealed trait ProjectStage extends _root_.scala.Product with _root_.scala.Serializable

  object ProjectStage {

    case object SyncShas extends ProjectStage { override def toString = "sync_shas" }
    case object SyncTags extends ProjectStage { override def toString = "sync_tags" }
    case object Tag extends ProjectStage { override def toString = "tag" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    case class UNDEFINED(override val toString: String) extends ProjectStage

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[ProjectStage] = scala.List(SyncShas, SyncTags, Tag)

    private[this]
    val byName: Map[String, ProjectStage] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): ProjectStage = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[ProjectStage] = byName.get(value.toLowerCase)

  }

}

package io.flow.delta.config.v0.models {

  package object json {
    import play.api.libs.json.__
    import play.api.libs.json.JsString
    import play.api.libs.json.Writes
    import play.api.libs.functional.syntax._
    import io.flow.delta.config.v0.models.json._

    private[v0] implicit val jsonReadsUUID = __.read[String].map(java.util.UUID.fromString)

    private[v0] implicit val jsonWritesUUID = new Writes[java.util.UUID] {
      def writes(x: java.util.UUID) = JsString(x.toString)
    }

    private[v0] implicit val jsonReadsJodaDateTime = __.read[String].map { str =>
      import org.joda.time.format.ISODateTimeFormat.dateTimeParser
      dateTimeParser.parseDateTime(str)
    }

    private[v0] implicit val jsonWritesJodaDateTime = new Writes[org.joda.time.DateTime] {
      def writes(x: org.joda.time.DateTime) = {
        import org.joda.time.format.ISODateTimeFormat.dateTime
        val str = dateTime.print(x)
        JsString(str)
      }
    }

    private[v0] implicit val jsonReadsJodaLocalDate = __.read[String].map { str =>
      import org.joda.time.format.ISODateTimeFormat.dateParser
      dateParser.parseLocalDate(str)
    }

    private[v0] implicit val jsonWritesJodaLocalDate = new Writes[org.joda.time.LocalDate] {
      def writes(x: org.joda.time.LocalDate) = {
        import org.joda.time.format.ISODateTimeFormat.date
        val str = date.print(x)
        JsString(str)
      }
    }

    implicit val jsonReadsDeltaConfigBuildStage = new play.api.libs.json.Reads[io.flow.delta.config.v0.models.BuildStage] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.delta.config.v0.models.BuildStage] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.BuildStage(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.BuildStage(v))
              case err: play.api.libs.json.JsError => err
            }
          }
        }
      }
    }

    def jsonWritesDeltaConfigBuildStage(obj: io.flow.delta.config.v0.models.BuildStage) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectBuildStage(obj: io.flow.delta.config.v0.models.BuildStage) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesDeltaConfigBuildStage: play.api.libs.json.Writes[BuildStage] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.BuildStage] {
        def writes(obj: io.flow.delta.config.v0.models.BuildStage) = {
          jsonWritesDeltaConfigBuildStage(obj)
        }
      }
    }

    implicit val jsonReadsDeltaConfigInstanceType = new play.api.libs.json.Reads[io.flow.delta.config.v0.models.InstanceType] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.delta.config.v0.models.InstanceType] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.InstanceType(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.InstanceType(v))
              case err: play.api.libs.json.JsError => err
            }
          }
        }
      }
    }

    def jsonWritesDeltaConfigInstanceType(obj: io.flow.delta.config.v0.models.InstanceType) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectInstanceType(obj: io.flow.delta.config.v0.models.InstanceType) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesDeltaConfigInstanceType: play.api.libs.json.Writes[InstanceType] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.InstanceType] {
        def writes(obj: io.flow.delta.config.v0.models.InstanceType) = {
          jsonWritesDeltaConfigInstanceType(obj)
        }
      }
    }

    implicit val jsonReadsDeltaConfigProjectStage = new play.api.libs.json.Reads[io.flow.delta.config.v0.models.ProjectStage] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.delta.config.v0.models.ProjectStage] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.ProjectStage(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.ProjectStage(v))
              case err: play.api.libs.json.JsError => err
            }
          }
        }
      }
    }

    def jsonWritesDeltaConfigProjectStage(obj: io.flow.delta.config.v0.models.ProjectStage) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectProjectStage(obj: io.flow.delta.config.v0.models.ProjectStage) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesDeltaConfigProjectStage: play.api.libs.json.Writes[ProjectStage] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.ProjectStage] {
        def writes(obj: io.flow.delta.config.v0.models.ProjectStage) = {
          jsonWritesDeltaConfigProjectStage(obj)
        }
      }
    }

    implicit def jsonReadsDeltaConfigBranch: play.api.libs.json.Reads[Branch] = {
      (__ \ "name").read[String].map { x => new Branch(name = x) }
    }

    def jsObjectBranch(obj: io.flow.delta.config.v0.models.Branch): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "name" -> play.api.libs.json.JsString(obj.name)
      )
    }

    implicit def jsonWritesDeltaConfigBranch: play.api.libs.json.Writes[Branch] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.Branch] {
        def writes(obj: io.flow.delta.config.v0.models.Branch) = {
          jsObjectBranch(obj)
        }
      }
    }

    implicit def jsonReadsDeltaConfigBuild: play.api.libs.json.Reads[Build] = {
      (
        (__ \ "name").read[String] and
        (__ \ "dockerfile").read[String] and
        (__ \ "initial_number_instances").read[Long] and
        (__ \ "instance_type").read[io.flow.delta.config.v0.models.InstanceType] and
        (__ \ "memory").readNullable[Long] and
        (__ \ "port_container").read[Int] and
        (__ \ "port_host").read[Int] and
        (__ \ "stages").read[Seq[io.flow.delta.config.v0.models.BuildStage]] and
        (__ \ "dependencies").read[Seq[String]] and
        (__ \ "version").readNullable[String] and
        (__ \ "healthcheck_url").readNullable[String]
      )(Build.apply _)
    }

    def jsObjectBuild(obj: io.flow.delta.config.v0.models.Build): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "name" -> play.api.libs.json.JsString(obj.name),
        "dockerfile" -> play.api.libs.json.JsString(obj.dockerfile),
        "initial_number_instances" -> play.api.libs.json.JsNumber(obj.initialNumberInstances),
        "instance_type" -> play.api.libs.json.JsString(obj.instanceType.toString),
        "port_container" -> play.api.libs.json.JsNumber(obj.portContainer),
        "port_host" -> play.api.libs.json.JsNumber(obj.portHost),
        "stages" -> play.api.libs.json.Json.toJson(obj.stages),
        "dependencies" -> play.api.libs.json.Json.toJson(obj.dependencies)
      ) ++ (obj.memory match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("memory" -> play.api.libs.json.JsNumber(x))
      }) ++
      (obj.version match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("version" -> play.api.libs.json.JsString(x))
      }) ++
      (obj.healthcheckUrl match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("healthcheck_url" -> play.api.libs.json.JsString(x))
      })
    }

    implicit def jsonWritesDeltaConfigBuild: play.api.libs.json.Writes[Build] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.Build] {
        def writes(obj: io.flow.delta.config.v0.models.Build) = {
          jsObjectBuild(obj)
        }
      }
    }

    implicit def jsonReadsDeltaConfigConfigError: play.api.libs.json.Reads[ConfigError] = {
      (__ \ "errors").read[Seq[String]].map { x => new ConfigError(errors = x) }
    }

    def jsObjectConfigError(obj: io.flow.delta.config.v0.models.ConfigError): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "errors" -> play.api.libs.json.Json.toJson(obj.errors)
      )
    }

    implicit def jsonReadsDeltaConfigConfigProject: play.api.libs.json.Reads[ConfigProject] = {
      (
        (__ \ "stages").read[Seq[io.flow.delta.config.v0.models.ProjectStage]] and
        (__ \ "builds").read[Seq[io.flow.delta.config.v0.models.Build]] and
        (__ \ "branches").read[Seq[io.flow.delta.config.v0.models.Branch]]
      )(ConfigProject.apply _)
    }

    def jsObjectConfigProject(obj: io.flow.delta.config.v0.models.ConfigProject): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "stages" -> play.api.libs.json.Json.toJson(obj.stages),
        "builds" -> play.api.libs.json.Json.toJson(obj.builds),
        "branches" -> play.api.libs.json.Json.toJson(obj.branches)
      )
    }

    implicit def jsonReadsDeltaConfigConfig: play.api.libs.json.Reads[Config] = new play.api.libs.json.Reads[Config] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[Config] = {
        (js \ "discriminator").asOpt[String].getOrElse { sys.error("Union[Config] requires a discriminator named 'discriminator' - this field was not found in the Json Value") } match {
          case "config_project" => js.validate[io.flow.delta.config.v0.models.ConfigProject]
          case "config_error" => js.validate[io.flow.delta.config.v0.models.ConfigError]
          case other => play.api.libs.json.JsSuccess(io.flow.delta.config.v0.models.ConfigUndefinedType(other))
        }
      }
    }

    def jsObjectConfig(obj: io.flow.delta.config.v0.models.Config): play.api.libs.json.JsObject = {
      obj match {
        case x: io.flow.delta.config.v0.models.ConfigProject => jsObjectConfigProject(x) ++ play.api.libs.json.Json.obj("discriminator" -> "config_project")
        case x: io.flow.delta.config.v0.models.ConfigError => jsObjectConfigError(x) ++ play.api.libs.json.Json.obj("discriminator" -> "config_error")
        case other => {
          sys.error(s"The type[${other.getClass.getName}] has no JSON writer")
        }
      }
    }

    implicit def jsonWritesDeltaConfigConfig: play.api.libs.json.Writes[Config] = {
      new play.api.libs.json.Writes[io.flow.delta.config.v0.models.Config] {
        def writes(obj: io.flow.delta.config.v0.models.Config) = {
          jsObjectConfig(obj)
        }
      }
    }
  }
}

package io.flow.delta.config.v0 {

  object Bindables {

    import play.api.mvc.{PathBindable, QueryStringBindable}

    // import models directly for backwards compatibility with prior versions of the generator
    import Core._
    import Models._

    object Core {
      implicit val pathBindableDateTimeIso8601: PathBindable[_root_.org.joda.time.DateTime] = ApibuilderPathBindable(ApibuilderTypes.dateTimeIso8601)
      implicit val queryStringBindableDateTimeIso8601: QueryStringBindable[_root_.org.joda.time.DateTime] = ApibuilderQueryStringBindable(ApibuilderTypes.dateTimeIso8601)

      implicit val pathBindableDateIso8601: PathBindable[_root_.org.joda.time.LocalDate] = ApibuilderPathBindable(ApibuilderTypes.dateIso8601)
      implicit val queryStringBindableDateIso8601: QueryStringBindable[_root_.org.joda.time.LocalDate] = ApibuilderQueryStringBindable(ApibuilderTypes.dateIso8601)
    }

    object Models {
      import io.flow.delta.config.v0.models._

      val buildStageConverter: ApibuilderTypeConverter[io.flow.delta.config.v0.models.BuildStage] = new ApibuilderTypeConverter[io.flow.delta.config.v0.models.BuildStage] {
        override def convert(value: String): io.flow.delta.config.v0.models.BuildStage = io.flow.delta.config.v0.models.BuildStage(value)
        override def convert(value: io.flow.delta.config.v0.models.BuildStage): String = value.toString
        override def example: io.flow.delta.config.v0.models.BuildStage = io.flow.delta.config.v0.models.BuildStage.SetDesiredState
        override def validValues: Seq[io.flow.delta.config.v0.models.BuildStage] = io.flow.delta.config.v0.models.BuildStage.all
      }
      implicit val pathBindableBuildStage: PathBindable[io.flow.delta.config.v0.models.BuildStage] = ApibuilderPathBindable(buildStageConverter)
      implicit val queryStringBindableBuildStage: QueryStringBindable[io.flow.delta.config.v0.models.BuildStage] = ApibuilderQueryStringBindable(buildStageConverter)

      val instanceTypeConverter: ApibuilderTypeConverter[io.flow.delta.config.v0.models.InstanceType] = new ApibuilderTypeConverter[io.flow.delta.config.v0.models.InstanceType] {
        override def convert(value: String): io.flow.delta.config.v0.models.InstanceType = io.flow.delta.config.v0.models.InstanceType(value)
        override def convert(value: io.flow.delta.config.v0.models.InstanceType): String = value.toString
        override def example: io.flow.delta.config.v0.models.InstanceType = io.flow.delta.config.v0.models.InstanceType.C4Large
        override def validValues: Seq[io.flow.delta.config.v0.models.InstanceType] = io.flow.delta.config.v0.models.InstanceType.all
      }
      implicit val pathBindableInstanceType: PathBindable[io.flow.delta.config.v0.models.InstanceType] = ApibuilderPathBindable(instanceTypeConverter)
      implicit val queryStringBindableInstanceType: QueryStringBindable[io.flow.delta.config.v0.models.InstanceType] = ApibuilderQueryStringBindable(instanceTypeConverter)

      val projectStageConverter: ApibuilderTypeConverter[io.flow.delta.config.v0.models.ProjectStage] = new ApibuilderTypeConverter[io.flow.delta.config.v0.models.ProjectStage] {
        override def convert(value: String): io.flow.delta.config.v0.models.ProjectStage = io.flow.delta.config.v0.models.ProjectStage(value)
        override def convert(value: io.flow.delta.config.v0.models.ProjectStage): String = value.toString
        override def example: io.flow.delta.config.v0.models.ProjectStage = io.flow.delta.config.v0.models.ProjectStage.SyncShas
        override def validValues: Seq[io.flow.delta.config.v0.models.ProjectStage] = io.flow.delta.config.v0.models.ProjectStage.all
      }
      implicit val pathBindableProjectStage: PathBindable[io.flow.delta.config.v0.models.ProjectStage] = ApibuilderPathBindable(projectStageConverter)
      implicit val queryStringBindableProjectStage: QueryStringBindable[io.flow.delta.config.v0.models.ProjectStage] = ApibuilderQueryStringBindable(projectStageConverter)
    }

    trait ApibuilderTypeConverter[T] {

      def convert(value: String): T

      def convert(value: T): String

      def example: T

      def validValues: Seq[T] = Nil

      def errorMessage(key: String, value: String, ex: java.lang.Exception): String = {
        val base = s"Invalid value '$value' for parameter '$key'. "
        validValues.toList match {
          case Nil => base + "Ex: " + convert(example)
          case values => base + ". Valid values are: " + values.mkString("'", "', '", "'")
        }
      }
    }

    object ApibuilderTypes {
      import org.joda.time.{format, DateTime, LocalDate}

      val dateTimeIso8601: ApibuilderTypeConverter[DateTime] = new ApibuilderTypeConverter[DateTime] {
        override def convert(value: String): DateTime = format.ISODateTimeFormat.dateTimeParser.parseDateTime(value)
        override def convert(value: DateTime): String = format.ISODateTimeFormat.dateTime.print(value)
        override def example: DateTime = DateTime.now
      }

      val dateIso8601: ApibuilderTypeConverter[LocalDate] = new ApibuilderTypeConverter[LocalDate] {
        override def convert(value: String): LocalDate = format.ISODateTimeFormat.yearMonthDay.parseLocalDate(value)
        override def convert(value: LocalDate): String = value.toString
        override def example: LocalDate = LocalDate.now
      }

    }

    case class ApibuilderQueryStringBindable[T](
      converters: ApibuilderTypeConverter[T]
    ) extends QueryStringBindable[T] {

      override def bind(key: String, params: Map[String, Seq[String]]): _root_.scala.Option[_root_.scala.Either[String, T]] = {
        params.getOrElse(key, Nil).headOption.map { v =>
          try {
            Right(
              converters.convert(v)
            )
          } catch {
            case ex: java.lang.Exception => Left(
              converters.errorMessage(key, v, ex)
            )
          }
        }
      }

      override def unbind(key: String, value: T): String = {
        converters.convert(value)
      }
    }

    case class ApibuilderPathBindable[T](
      converters: ApibuilderTypeConverter[T]
    ) extends PathBindable[T] {

      override def bind(key: String, value: String): _root_.scala.Either[String, T] = {
        try {
          Right(
            converters.convert(value)
          )
        } catch {
          case ex: java.lang.Exception => Left(
            converters.errorMessage(key, value, ex)
          )
        }
      }

      override def unbind(key: String, value: T): String = {
        converters.convert(value)
      }
    }

  }

}
