package io.flow.delta.actors.functions

import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}
import io.flow.delta.api.lib.GithubUtil

import io.flow.postgresql.Authorization
import db.{ShasDao, UsersDao}
import io.flow.delta.v0.models.Project
import scala.concurrent.Future

object SyncMasterSha extends SupervisorFunction {

  def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    SyncMasterSha(project).run
  }

}

/**
  * Look up the sha for the master branch from github, and record it
  * in the shas table.
  */
case class SyncMasterSha(project: Project) extends Github {

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    GithubUtil.parseUri(project.uri) match {
      case Left(error) => {
        Future { SupervisorResult.NoChange(s"Could not parse project uri[${project.uri}]") }
      }

      case Right(repo) => {
        withGithubClient(project.user.id) { client =>
          val existing = ShasDao.findByProjectIdAndMaster(Authorization.All, project.id).map(_.hash)

          client.refs.getByRef(repo.owner, repo.project, "heads/master").map { master =>
            val masterSha = master.`object`.sha
            existing == Some(masterSha) match {
              case true => {
                SupervisorResult.NoChange(s"Shas table already records that master is at $masterSha")
              }
              case false => {
                ShasDao.upsertMaster(UsersDao.systemUser, project.id, masterSha)
                SupervisorResult.Change(s"Updated master sha to $masterSha")
              }
            }
          }
        }
      }
    }
  }

}
