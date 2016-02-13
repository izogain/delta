package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{OrganizationSummary, ProjectSummary}
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

case class Tag(
  id: String,
  project: ProjectSummary,
  name: String
)

case class TagForm(
  projectId: String,
  name: String
)

object TagsDao {

  private[this] val BaseQuery = Query(s"""
    select tags.id,
           tags.name,
           projects.id as project_id,
           projects.id as project_name,
           projects.uri as project_uri,
           projects.organization_id as project_organization_id
      from tags
      join projects on tags.project_id = projects.id
  """)

  private[this] val InsertQuery = """
    insert into tags
    (id, project_id, name, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {updated_by_user_id})
  """

  private[db] def validate(
    user: User,
    form: TagForm
  ): Seq[String] = {
    val nameErrors = if (form.name.trim == "") {
      Seq("Name cannot be empty")
    } else {
      Nil
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => Nil
    }

    val existingErrors = findByProjectIdAndName(Authorization.All, form.projectId, form.name) match {
      case None => Nil
      case Some(found) => Seq("Project already has a tag with this name")
    }

    nameErrors ++ projectErrors ++ existingErrors
  }

  def create(createdBy: User, form: TagForm): Either[Seq[String], Tag] = {
    validate(createdBy, form) match {
      case Nil => {
        val id = io.flow.play.util.IdGenerator("tag").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.projectId,
            'name -> form.name.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.TagCreated(form.projectId, id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create tag")
          }
        )
      }
      case errors => {
        Left(errors)
      }
    }
  }

  def delete(deletedBy: User, tag: Tag) {
    Delete.delete("tags", deletedBy.id, tag.id)
  }

  def findByProjectIdAndName(auth: Authorization, projectId: String, name: String): Option[Tag] = {
    findAll(auth, projectId = Some(projectId), name = Some(name), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Tag] = {
    findAll(auth, ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    name: Option[String] = None,
    orderBy: OrderBy = OrderBy("lower(tags.name), tags.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Tag] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "tags",
        auth = Filters(auth).organizations("projects.organization_id"),
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("projects.id", projectId).
        optionalText(
          "tags.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        as(
          parser().*
        )
    }
  }

  private[this] def parser(): RowParser[Tag] = {
    SqlParser.str("id") ~
    io.flow.delta.v0.anorm.parsers.ProjectSummary.parserWithPrefix("project") ~
    SqlParser.str("name") map {
      case id ~ project ~ name => {
        Tag(
          id = id,
          project = project,
          name = name
        )
      }
    }
  }

}
