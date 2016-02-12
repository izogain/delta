package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{OrganizationSummary, ProjectSummary}
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

case class Sha(
  id: String,
  project: ProjectSummary,
  branch: String,
  sha: String
)

case class ShaForm(
  projectId: String,
  branch: String,
  sha: String
)

object ShasDao {

  private[this] val BaseQuery = Query(s"""
    select shas.id,
           shas.branch,
           shas.sha,
           projects.id as project_id,
           projects.id as project_name,
           projects.organization_id as project_organization_id
      from shas
      join projects on shas.project_id = projects.id
  """)

  private[this] val InsertQuery = """
    insert into shas
    (id, project_id, branch, sha, updated_by_user_id)
    values
    ({id}, {project_id}, {branch}, {sha}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update shas
       set project_id = {project_id},
           branch = {branch},
           sha = {sha},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[db] def validate(
    user: User,
    form: ShaForm,
    existing: Option[Sha] = None
  ): Seq[String] = {
    val shaErrors = if (form.sha.trim == "") {
      Seq("Sha cannot be empty")
    } else {
      Nil
    }

    val branchErrors = if (form.branch.trim == "") {
      Seq("Branch cannot be empty")
    } else {
      Nil
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => {
        MembershipsDao.isMember(project.organization.id, user) match  {
          case false => Seq("You do not have access to this organization")
          case true => Nil
        }
      }
    }

    val existingErrors = findByProjectIdAndBranch(Authorization.All, form.projectId, form.branch) match {
      case None => Nil
      case Some(found) => {
        existing.map(_.id) == Some(found.id) match {
          case true => Nil
          case false => Seq("Project already has a sha for this branchg")
        }
      }
    }

    shaErrors ++ branchErrors ++ projectErrors ++ existingErrors
  }

  def create(createdBy: User, form: ShaForm): Either[Seq[String], Sha] = {
    validate(createdBy, form) match {
      case Nil => {

        val id = io.flow.play.util.IdGenerator("sha").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.projectId,
            'branch -> form.branch.trim,
            'sha -> form.sha.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create sha")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def update(createdBy: User, sha: Sha, form: ShaForm): Either[Seq[String], Sha] = {
    validate(createdBy, form, Some(sha)) match {
      case Nil => {
        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'id -> sha.id,
            'project_id -> form.projectId,
            'branch -> form.branch.trim,
            'sha -> form.sha.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        Right(
          findById(Authorization.All, sha.id).getOrElse {
            sys.error("Failed to create sha")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: User, sha: Sha) {
    Delete.delete("shas", deletedBy.id, sha.id)
  }

  def findByProjectIdAndBranch(auth: Authorization, projectId: String, branch: String): Option[Sha] = {
    findAll(auth, projectId = Some(projectId), branch = Some(branch), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Sha] = {
    findAll(auth, ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    branch: Option[String] = None,
    orderBy: OrderBy = OrderBy("lower(shas.branch), shas.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Sha] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "shas",
        auth = Filters(auth).organizations("projects.organization_id"),
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("projects.id", projectId).
        optionalText(
          "shas.branch",
          branch,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        as(
          parser().*
        )
    }
  }

  private[this] def parser(): RowParser[Sha] = {
    SqlParser.str("id") ~
    io.flow.delta.v0.anorm.parsers.ProjectSummary.parserWithPrefix("project") ~
    SqlParser.str("branch") ~
    SqlParser.str("sha") map {
      case id ~ project ~ branch ~ sha => {
        Sha(
          id = id,
          project = project,
          branch = branch,
          sha = sha
        )
      }
    }
  }

}
