package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models._
import io.flow.delta.api.lib.GithubUtil
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object ImagesDao {

  private[this] val BaseQuery = Query(s"""
    select images.id,
           images.project_id,
           images.name,
           images.version
      from images
  """)

  private[this] val InsertQuery = """
    insert into images
    (id, project_id, name, version, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {version}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update images
       set project_id = {project_id},
           name = {name},
           version = {version}
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  def toSummary(project: Project): ProjectSummary = {
    ProjectSummary(
      id = project.id,
      organization = OrganizationSummary(project.organization.id),
      name = project.name,
      uri = project.uri
    )
  }

  private[db] def validate(
    user: User,
    form: ImageForm,
    existing: Option[Image] = None
    ): Seq[String] = {
    val nameErrors = if (form.name.trim == "") {
      Seq("Name cannot be empty")
    } else {
      Nil
    }

    val veresionErrors = if (form.version.trim == "") {
      Seq("Version cannot be empty")
    } else {
      Nil
    }

    nameErrors ++ veresionErrors
  }

  def create(createdBy: User, form: ImageForm): Either[Seq[String], Image] = {
    validate(createdBy, form) match {
      case Nil => {

        val id = io.flow.play.util.IdGenerator("img").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.project.id,
            'name -> form.name.trim,
            'version -> form.version.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.ImageCreated(id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create image")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def findById(auth: Authorization, id: String): Option[Image] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
   auth: Authorization,
   id: Option[String] = None,
   ids: Option[Seq[String]] = None,
   name: Option[String] = None,
   orderBy: OrderBy = OrderBy("lower(projects.name), projects.created_at"),
   limit: Long = 25,
   offset: Long = 0
  ): Seq[Image] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "images",
        auth = Filters(auth).organizations("projects.organization_id", Some("projects.visibility")),
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        optionalText(
          "images.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        as(
          io.flow.delta.v0.anorm.parsers.Image.parser().*
        )
    }
  }
}
