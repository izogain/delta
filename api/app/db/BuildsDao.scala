package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{Build, BuildForm}
import io.flow.postgresql.{Authorization, Query, OrderBy, Pager}
import io.flow.common.v0.models.User
import io.flow.play.util.UrlKey
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object BuildsDao {

  private[db] val Master = "master"

  private[this] val BaseQuery = Query(s"""
    select builds.id,
           builds.name,
           builds.dockerfile_path,
           projects.id as project_id,
           projects.name as project_name,
           projects.uri as project_uri,
           projects.organization_id as project_organization_id
      from builds
      join projects on builds.project_id = projects.id
  """)

  def findAllByProjectId(auth: Authorization, projectId: String): Iterator[Build] = {
    Pager.create { offset =>
      BuildsDao.findAll(auth, projectId = Some(projectId), offset = offset)
    }
  }
  
  def findByProjectIdAndName(auth: Authorization, projectId: String, name: String): Option[Build] = {
    findAll(auth, projectId = Some(projectId), name = Some(name), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Build] = {
    findAll(auth, ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    name: Option[String] = None,
    orderBy: OrderBy = OrderBy("lower(projects.name), builds.position"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Build] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "builds",
        auth = Filters(auth).organizations("projects.organization_id"),
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("projects.id", projectId).
        optionalText(
          "builds.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        as(
          io.flow.delta.v0.anorm.parsers.Build.parser().*
        )
    }
  }

}

case class BuildsWriteDao @javax.inject.Inject() (
  imagesWriteDao: ImagesWriteDao,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) {

  private[this] val InsertQuery = """
    insert into builds
    (id, project_id, name, dockerfile_path, position, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {dockerfile_path}, {position}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update builds
       set project_id = {project_id},
           name = {name},
           dockerfile_path = {dockerfile_path},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[this] val MaxPositionQuery = """
    select max(position) as position from builds where project_id = {project_id}
  """

  private[this] val urlKey = UrlKey(minKeyLength = 3)

  private[db] def validate(
    user: User,
    form: BuildForm,
    existing: Option[Build] = None
  ): Seq[String] = {
    val dockerfilePathErrors = if (form.dockerfilePath.trim == "") {
      Seq("Dockerfile path cannot be empty")
    } else {
      Nil
    }

    val nameErrors = if (form.name.trim == "") {
      Seq("Name cannot be empty")
    } else {
      BuildsDao.findByProjectIdAndName(Authorization.All, form.projectId, form.name) match {
        case None => {
          urlKey.validate(form.name.trim, "Name")
        }
        case Some(found) => {
          existing.map(_.id) == Some(found.id) match {
            case true => Nil
            case false => Seq("Project already has a build with this name")
          }
        }
      }
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => Nil
    }

    dockerfilePathErrors ++ nameErrors ++ projectErrors
  }

  def create(createdBy: User, form: BuildForm): Either[Seq[String], Build] = {
    validate(createdBy, form) match {
      case Nil => {
        val id = io.flow.play.util.IdGenerator("bld").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.projectId,
            'name -> form.name.trim,
            'dockerfile_path -> form.dockerfilePath.trim,
            'position -> nextPosition(form.projectId),
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        mainActor ! MainActor.Messages.BuildCreated(form.projectId, id)

        Right(
          BuildsDao.findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create build")
          }
        )
      }
      case errors => {
        Left(errors)
      }
    }
  }

  private[this] def nextPosition(projectId: String)(
    implicit c: java.sql.Connection
  ): Long = {
    SQL(MaxPositionQuery).on(
      'project_id -> projectId
    ).as(SqlParser.get[Option[Long]]("position").single).headOption match {
      case None => 0
      case Some(n) => n + 1
    }
  }

  def update(createdBy: User, build: Build, form: BuildForm): Either[Seq[String], Build] = {
    validate(createdBy, form, Some(build)) match {
      case Nil => {
        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'id -> build.id,
            'project_id -> form.projectId,
            'name -> form.name.trim,
            'dockerfile_path -> form.dockerfilePath.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        mainActor ! MainActor.Messages.BuildUpdated(build.project.id, build.id)

        Right(
          BuildsDao.findById(Authorization.All, build.id).getOrElse {
            sys.error("Failed to update build")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: User, build: Build) {
    Pager.create { offset =>
      ImagesDao.findAll(buildId = Some(build.id), offset = offset)
    }.foreach { image =>
      imagesWriteDao.delete(deletedBy, image)
    }

    Delete.delete("builds", deletedBy.id, build.id)
    mainActor ! MainActor.Messages.BuildDeleted(build.project.id, build.id)
  }

}
