package db

import io.flow.delta.actors.MainActor
import io.flow.delta.v0.models.{Scms, Binary, BinaryForm, Library, LibraryForm, Project, ProjectForm, ProjectSummary, OrganizationSummary, Visibility}
import io.flow.delta.api.lib.GithubUtil
import io.flow.postgresql.{Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object ProjectsDao {

  private[this] val BaseQuery = Query(s"""
    select projects.id,
           projects.user_id,
           projects.visibility,
           projects.scms,
           projects.name,
           projects.uri,
           organizations.id as organization_id,
           organizations.key as organization_key
      from projects
      left join organizations on organizations.deleted_at is null and organizations.id = projects.organization_id
  """)

  private[this] val FilterProjectLibraries = """
    projects.id in (select project_id from project_libraries where deleted_at is null and %s)
  """.trim

  private[this] val FilterProjectBinaries = """
    projects.id in (select project_id from project_binaries where deleted_at is null and %s)
  """.trim

  private[this] val InsertQuery = """
    insert into projects
    (id, user_id, organization_id, visibility, scms, name, uri, updated_by_user_id)
    values
    ({id}, {user_id}, {organization_id}, {visibility}, {scms}, {name}, {uri}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update projects
       set visibility = {visibility},
           scms = {scms},
           name = {name},
           uri = {uri},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  def toSummary(project: Project): ProjectSummary = {
    ProjectSummary(
      id = project.id,
      organization = OrganizationSummary(project.organization.id, project.organization.key),
      name = project.name
    )
  }

  private[db] def validate(
    user: User,
    form: ProjectForm,
    existing: Option[Project] = None
  ): Seq[String] = {
    val uriErrors = if (form.uri.trim == "") {
      Seq("Uri cannot be empty")
    } else {
      form.scms match {
        case Scms.UNDEFINED(_) => Seq("Scms not found")
        case Scms.Github => {
          GithubUtil.parseUri(form.uri) match {
            case Left(error) => Seq(error)
            case Right(_) => Nil
          }
        }
      }
    }

    val visibilityErrors = form.visibility match {
      case Visibility.UNDEFINED(_) => Seq("Visibility must be one of: ${Visibility.all.map(_.toString).mkString(", ")}")
      case _ => Nil
    }

    val nameErrors = if (form.name.trim == "") {
      Seq("Name cannot be empty")

    } else {
      ProjectsDao.findByOrganizationAndName(Authorization.All, form.organization, form.name) match {
        case None => Seq.empty
        case Some(p) => {
          Some(p.id) == existing.map(_.id) match {
            case true => Nil
            case false => Seq("Project with this name already exists")
          }
        }
      }
    }

    val organizationErrors = MembershipsDao.isMemberByOrgKey(form.organization, user) match  {
      case false => Seq("You do not have access to this organization")
      case true => Nil
    }

    nameErrors ++ visibilityErrors ++ uriErrors ++ organizationErrors
  }

  def create(createdBy: User, form: ProjectForm): Either[Seq[String], Project] = {
    validate(createdBy, form) match {
      case Nil => {

        val org = OrganizationsDao.findByKey(Authorization.All, form.organization).getOrElse {
          sys.error("Could not find organization with key[${form.organization}]")
        }
        
        val id = io.flow.play.util.IdGenerator("prj").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'organization_id -> org.id,
            'user_id -> createdBy.id,
            'visibility -> form.visibility.toString,
            'scms -> form.scms.toString,
            'name -> form.name.trim,
            'uri -> form.uri.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.ProjectCreated(id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create project")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def update(createdBy: User, project: Project, form: ProjectForm): Either[Seq[String], Project] = {
    validate(createdBy, form, Some(project)) match {
      case Nil => {
        // To support org change - need to record the change as its
        // own record to be able to track changes.
        assert(
          project.organization.key == form.organization,
          "Changing organization not currently supported"
        )

        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'id -> project.id,
            'visibility -> form.visibility.toString,
            'scms -> form.scms.toString,
            'name -> form.name.trim,
            'uri -> form.uri.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.ProjectUpdated(project.id)

        Right(
          findById(Authorization.All, project.id).getOrElse {
            sys.error("Failed to create project")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def softDelete(deletedBy: User, project: Project) {
    SoftDelete.delete("projects", deletedBy.id, project.id)
    MainActor.ref ! MainActor.Messages.ProjectDeleted(project.id)
  }

/*
  def findByOrganizationIdAndName(auth: Authorization, organizationId: String, name: String): Option[Project] = {
    findAll(auth, organizationId = Some(organizationId), name = Some(name), limit = 1).headOption
  }
 */
  def findByOrganizationAndName(auth: Authorization, organization: String, name: String): Option[Project] = {
    findAll(auth, organization = Some(organization), name = Some(name), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Project] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    organization: Option[String] = None,
    organizationId: Option[String] = None,
    name: Option[String] = None,
    groupId: Option[String] = None,
    artifactId: Option[String] = None,
    version: Option[String] = None,
    libraryId: Option[String] = None,
    binary: Option[String] = None,
    binaryId: Option[String] = None,
    isDeleted: Option[Boolean] = Some(false),
    orderBy: OrderBy = OrderBy("lower(projects.name), projects.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Project] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "projects",
        auth = auth.organizations("projects.organization_id", Some("projects.visibility")),
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        isDeleted = isDeleted,
        limit = limit,
        offset = offset
      ).
        optionalText(
          "organizations.key",
          organization,
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        equals("organizations.id", organizationId).
        optionalText(
          "projects.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        and(
          groupId.map { v => FilterProjectLibraries.format("project_libraries.group_id = trim({group_id})") }
        ).bind("group_id", groupId).
        and(
          artifactId.map { v => FilterProjectLibraries.format("project_libraries.artifact_id = trim({artifact_id})") }
        ).bind("artifact_id", artifactId).
        and(
          version.map { v => FilterProjectLibraries.format("project_libraries.version = trim({version})") }
        ).bind("version", version).
        and(
          libraryId.map { v => FilterProjectLibraries.format("project_libraries.library_id = {library_id}") }
        ).bind("library_id", libraryId).
        and(
          binary.map { v => FilterProjectBinaries.format("project_binaries.name = trim({binary})") }
        ).bind("binary", binary).
        and(
          binaryId.map { v => FilterProjectBinaries.format("project_binaries.binary_id = {binary_id}") }
        ).bind("binary_id", binaryId).
        as(
          io.flow.delta.v0.anorm.parsers.Project.parser().*
        )
    }
  }

}
