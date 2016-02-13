package db

import io.flow.delta.v0.models.{Settings, SettingsForm}
import io.flow.play.util.IdGenerator
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object SettingsDao {

  private[this] val BaseQuery = Query(s"""
    select settings.auto_tag
      from settings
      join projects on projects.id = settings.project_id
  """)

  private[this] val InsertQuery = """
    insert into settings
    (id, project_id, auto_tag, updated_by_user_id)
    values
    ({id}, {project_id}, {auto_tag}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update settings
       set auto_tag = {auto_tag},
           updated_by_user_id = {updated_by_user_id}
     where project_id = {project_id}
  """

  private[this] val LookupIdQuery = s"select id from settings where project_id = {project_id}"

  private[this] val idGenerator = IdGenerator("set")

  /**
    * Each project has 0 or 1 settings records... Upsert creates if
    * necessary, then updates
    */
  def upsert(createdBy: User, projectId: String, form: SettingsForm): Settings = {
    val settings = findByProjectId(Authorization.All, projectId).getOrElse {
      val defaults = Settings()
      create(createdBy, projectId, defaults)
      defaults
    }

    update(createdBy, projectId, settings, form)

    findByProjectId(Authorization.All, projectId).getOrElse {
      sys.error("Failed to upsert settings")
    }
  }

  private[this] def create(createdBy: User, projectId: String, settings: Settings) {
    DB.withConnection { implicit c =>
      SQL(InsertQuery).on(
        'id -> idGenerator.randomId(),
        'project_id -> projectId,
        'auto_tag -> settings.autoTag,
        'updated_by_user_id -> createdBy.id
      ).execute()
    }
  }

  private[this] def update(createdBy: User, projectId: String, settings: Settings, form: SettingsForm) {
    DB.withConnection { implicit c =>
      SQL(UpdateQuery).on(
        'project_id -> projectId,
        'auto_tag -> form.autoTag.getOrElse(settings.autoTag),
        'updated_by_user_id -> createdBy.id
      ).execute()
    }
  }

  def deleteByProjectId(deletedBy: User, projectId: String) {
    lookupId(projectId).map { id =>
      Delete.delete("settings", deletedBy.id, id)
    }
  }

  private[this] def lookupId(projectId: String): Option[String] = {
    DB.withConnection { implicit c =>
      SQL(LookupIdQuery).on(
        'project_id -> projectId
      ).as(SqlParser.get[String]("id").*).toList.headOption
    }
  }

  def findByProjectIdOrDefault(auth: Authorization, projectId: String): Settings = {
    findByProjectId(auth, projectId).getOrElse(Settings())
  }

  def findByProjectId(auth: Authorization, projectId: String): Option[Settings] = {
    findAll(auth, projectId = Some(projectId), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Settings] = {
    findAll(auth, ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    orderBy: OrderBy = OrderBy("settings.id"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Settings] = {

    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "settings",
        auth = Filters(auth).organizations("projects.organization_id"),
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("projects.id", projectId).
        as(
          io.flow.delta.v0.anorm.parsers.Settings.parser().*
        )
    }
  }

}
