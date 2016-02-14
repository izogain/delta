package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models._
import io.flow.postgresql.{Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current

object ImagesDao {

  private[this] val BaseQuery = Query(s"""
    select images.id,
           images.project_id,
           images.name,
           images.version,
           projects.id as project_id,
           projects.name as project_name,
           projects.uri as project_uri,
           projects.organization_id as project_organization_id
      from images
      join projects on projects.id = images.project_id
  """)

  private[this] val InsertQuery = """
    insert into images
    (id, project_id, name, version, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {version}, {updated_by_user_id})
  """

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

    val versionErrors = if (form.version.trim == "") {
      Seq("Version cannot be empty")
    } else {
      Nil
    }

    nameErrors ++ versionErrors
  }

  def create(createdBy: User, form: ImageForm): Either[Seq[String], Image] = {
    validate(createdBy, form) match {
      case Nil => {
       val id = io.flow.play.util.IdGenerator("img").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.projectId,
            'name -> form.name.trim,
            'version -> form.version.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.ImageCreated(id)

        Right(
          findById(id).getOrElse {
            sys.error("Failed to create image")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: User, image: Image) {
    Delete.delete("images", deletedBy.id, image.id)
  }

  def findById(id: String): Option[Image] = {
    findAll(ids = Some(Seq(id)), limit = 1).headOption
  }

  def findByProjectIdAndVersion(projectId: String, version: String): Option[Image] = {
    findAll(
      projectId = Some(projectId),
      versions = Some(Seq(version)),
      limit = 1
    ).headOption
  }

  def findAll(
   ids: Option[Seq[String]] = None,
   projectId: Option[String] = None,
   names: Option[Seq[String]] = None,
   versions: Option[Seq[String]] = None,
   orderBy: OrderBy = OrderBy("-lower(images.name), images.created_at"),
   limit: Long = 25,
   offset: Long = 0
  ): Seq[Image] = {
    DB.withConnection { implicit c =>
      BaseQuery.
        optionalIn("images.id", ids).
        optionalIn("images.name", names).
        equals("images.project_id", projectId).
        optionalIn("images.version", versions).
        orderBy(orderBy.sql).
        limit(limit).
        offset(offset).
        as(
          io.flow.delta.v0.anorm.parsers.Image.parser().*
        )
    }
  }
}
