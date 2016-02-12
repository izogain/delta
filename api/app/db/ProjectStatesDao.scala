package db

import io.flow.delta.v0.models.{Project, State, StateForm, Version}
import io.flow.delta.v0.models.json._
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object ProjectExpectedStatesDao extends ProjectStatesDao("project_expected_states", "pes")
object ProjectActualStatesDao extends ProjectStatesDao("project_actual_states", "pes")

class ProjectStatesDao(table: String, idPrefix: String) {

  private[this] val BaseQuery = Query(s"""
    select ${table}.id,
           ${table}.versions,
           ${table}.timestamp,
           projects.id as project_id,
           projects.id as project_name,
           projects.organization_id as project_organization_id
      from $table
      join projects on ${table}.project_id = projects.id
  """)

  private[this] val InsertQuery = s"""
    insert into $table
    (id, project_id, versions, timestamp, updated_by_user_id)
    values
    ({id}, {project_id}, {versions}::json, now(), {updated_by_user_id})
  """

  private[this] val UpdateQuery = s"""
    update $table
       set versions = {versions}::json,
           timestamp = now(),
           updated_by_user_id = {updated_by_user_id}
     where project_id = {project_id}
  """

  private[this] val idGenerator = io.flow.play.util.IdGenerator(idPrefix)

  private[db] def validate(
    user: User,
    project: Project,
    form: StateForm
  ): Seq[String] = {
    val versionErrors = if (form.versions.isEmpty) {
      Seq("Must have at least one version")
    } else {
      Nil
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, project.id) match {
      case None => Seq("Project not found")
      case Some(project) => {
        MembershipsDao.isMember(project.organization.id, user) match  {
          case false => Seq("User does not have access to this organization")
          case true => Nil
        }
      }
    }

    versionErrors ++ projectErrors
  }

  def create(createdBy: User, project: Project, form: StateForm): Either[Seq[String], State] = {
    validate(createdBy, project, form) match {
      case Nil => {

        val id = idGenerator.randomId()
        val sortedVersions = form.versions.sortBy { v => (v.name, v.instances) }

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> project.id,
            'versions -> Json.toJson(sortedVersions).toString,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error(s"Failed to create $table")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def upsert(createdBy: User, project: Project, form: StateForm): Either[Seq[String], State] = {
    findByProjectId(Authorization.All, project.id) match {
      case None => create(createdBy, project, form)
      case Some(_) => update(createdBy, project, form)
    }
  }

  private[this] def update(createdBy: User, project: Project, form: StateForm): Either[Seq[String], State] = {
    validate(createdBy, project, form) match {
      case Nil => {
        val sortedVersions = form.versions.sortBy { v => (v.name, v.instances) }
        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'project_id -> project.id,
            'versions -> Json.toJson(sortedVersions).toString,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        Right(
          findByProjectId(Authorization.All, project.id).getOrElse {
            sys.error(s"Failed to update $table")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: User, project: Project) {
    Delete.delete(table, deletedBy.id, project.id)
  }

  def findByProjectId(auth: Authorization, projectId: String): Option[State] = {
    findAll(auth, projectId = Some(projectId), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[State] = {
    findAll(auth, ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    orderBy: OrderBy = OrderBy(s"-${table}.timestamp,-${table}.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[State] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = table,
        auth = Filters(auth).organizations("projects.organization_id"),
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("projects.id", projectId).
        as(
          io.flow.delta.v0.anorm.parsers.State.parser().*
        )
    }
  }

}
