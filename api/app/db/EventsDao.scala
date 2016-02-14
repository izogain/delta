package db

import anorm._
import io.flow.delta.v0.models.{Event, EventType}
import io.flow.delta.actors.MainActor
import io.flow.postgresql.{Query, OrderBy}
import io.flow.common.v0.models.User
import java.io.{PrintWriter, StringWriter}
import play.api.db._
import play.api.libs.json._
import play.api.Play.current

object EventsDao {

  private[this] val BaseQuery = Query(s"""
    select events.id,
           events.created_at,
           events.type,
           events.summary,
           events.error,
           projects.id as project_id,
           projects.name as project_name,
           projects.uri as project_uri,
           projects.organization_id as project_organization_id
      from events
      join projects on events.project_id = projects.id
  """)

  private[this] val InsertQuery = """
    insert into events
    (id, project_id, type, summary, error, updated_by_user_id)
    values
    ({id}, {project_id}, {type}, {summary}, {error}, {updated_by_user_id})
  """

  /**
    * Create an event, returning its id
    */
  def create(createdBy: User, projectId: String, `type`: EventType, summary: String, ex: Option[Throwable]): String = {
    `type` match {
      case EventType.UNDEFINED(_) => sys.error("Invalid type: " + `type`)
      case _ => {}
    }

    val error = ex.map { e =>
      val sw = new StringWriter
      e.printStackTrace(new PrintWriter(sw))
      sw.toString.trim
    }

    val id = io.flow.play.util.IdGenerator("evt").randomId()

    DB.withConnection { implicit c =>
      SQL(InsertQuery).on(
        'id -> id,
        'project_id -> projectId,
        'type -> `type`.toString,
        'summary -> summary.trim,
        'error -> error,
        'updated_by_user_id -> createdBy.id
      ).execute()
    }

    id
  }

  def findById(id: String): Option[Event] = {
    findAll(ids = Some(Seq(id)), limit = 1).headOption
  }

  def findAll(
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    `type`: Option[EventType] = None,
    orderBy: OrderBy = OrderBy("-events.created_at, events.id"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Event] = {
    DB.withConnection { implicit c =>
      BaseQuery.
        optionalIn(s"events.id", ids).
        equals(s"events.project_id", projectId).
        equals(s"events.type", `type`.map(_.toString)).
        orderBy(orderBy.sql).
        limit(limit).
        offset(offset).
        as(
          io.flow.delta.v0.anorm.parsers.Event.parser().*
        )
    }
  }

}
