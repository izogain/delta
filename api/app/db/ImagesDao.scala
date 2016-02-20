package db

import io.flow.delta.actors.MainActor
import io.flow.delta.api.lib.Semver
import io.flow.delta.v0.models._
import io.flow.postgresql.{Authorization, Query, OrderBy}
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
    (id, project_id, name, version, sort_key, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {version}, {sort_key}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update images
       set project_id = {project_id},
           name = {name},
           version = {version},
           sort_key = {sort_key},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
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
      Semver.isSemver(form.version.trim) match {
        case true => Nil
        case false => Seq("Version must match semver pattern (e.g. 0.1.2)")
      }
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => Nil
    }

    nameErrors ++ versionErrors ++ projectErrors
  }

  /**
    * If the tag exists, updates the hash to match (if
    * necessary). Otherwise creates the tag.
    */
  def upsert(createdBy: User, projectId: String, name: String, version: String): Image = {
    val form = ImageForm(
      projectId = projectId,
      name = name,
      version = version
    )
    findByProjectIdAndVersion(projectId, version) match {
      case None => {
        create(createdBy, form) match {
          case Left(errors) => sys.error(errors.mkString(", "))
          case Right(image) => image
        }
      }
      case Some(existing) => {
        existing.name == name match {
          case true => existing
          case false => update(createdBy, existing, form) match {
            case Left(errors) => sys.error(errors.mkString(", "))
            case Right(image) => image
          }
        }
      }
    }
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
            'sort_key -> Util.generateVersionSortKey(form.version.trim),
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        // MainActor.ref ! MainActor.Messages.ImageCreated(form.projectId, id, form.version.trim)

        Right(
          findById(id).getOrElse {
            sys.error("Failed to create image")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  private[this] def update(createdBy: User, image: Image, form: ImageForm): Either[Seq[String], Image] = {
    validate(createdBy, form, Some(image)) match {
      case Nil => {
        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'id -> image.id,
            'project_id -> form.projectId,
            'name -> form.name.trim,
            'version -> form.version.trim,
            'sort_key -> Util.generateVersionSortKey(form.version.trim),
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        // MainActor.ref ! MainActor.Messages.ImageCreated(form.projectId, image.id, form.version.trim)

        Right(
          findById(image.id).getOrElse {
            sys.error("Failed to create image")
          }
        )
      }
      case errors => {
        Left(errors)
      }
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
   orderBy: OrderBy = OrderBy("-images.sort_key, -images.created_at"),
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
