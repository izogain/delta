package db

import io.flow.delta.v0.models.DashboardBuild
import io.flow.postgresql.{Authorization, Query}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object DashboardBuildsDao {

  private[this] val BaseQuery = Query(s"""
    select builds.id,
           builds.name,
           builds.dockerfile_path,
           projects.id as project_id,
           projects.name as project_name,
           projects.uri as project_uri,
           projects.organization_id as project_organization_id,
           build_last_states.timestamp as last_timestamp,
           build_last_states.versions as last_version,
           build_desired_states.timestamp as desired_timestamp,
           build_desired_states.versions as desired_version
      from builds
      join projects on builds.project_id = projects.id
      join build_last_states on build_last_states.build_id = builds.id
      join build_desired_states on build_desired_states.build_id = builds.id
  """)

  def findAll(
    auth: Authorization,
    limit: Long = 25,
    offset: Long = 0
  ): Seq[DashboardBuild] = {

    DB.withConnection { implicit c =>
      BaseQuery.
        and(Filters(auth).organizations("projects.organization_id").sql).
        limit(limit).
        offset(offset).
        as(
          parser().*
        )
    }
  }

  private[this] def parser(): RowParser[DashboardBuild] = {
    io.flow.delta.v0.anorm.parsers.ProjectSummary.parserWithPrefix("project") ~
    SqlParser.str("name") ~
    io.flow.delta.v0.anorm.parsers.State.parserWithPrefix("last") ~
    io.flow.delta.v0.anorm.parsers.State.parserWithPrefix("desired") map {
      case projectSummary ~ name ~ desiredState ~ lastState => {
        DashboardBuild(
          project = projectSummary,
          name = name,
          desired = desiredState,
          last = lastState
        )
      }
    }
  }
}
