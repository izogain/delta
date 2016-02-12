package io.flow.delta.actors

import io.flow.delta.api.lib.Semver
import io.flow.github.v0.Client 
import io.flow.postgresql.Authorization
import db.{ProjectsDao, ShasDao, UsersDao}
import io.flow.delta.api.lib.{EventLog, GithubUtil, GithubHelper, Repo}
import io.flow.delta.v0.models.Project
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.Future

/**
  * Capture top level methods we need to supervise a given project
  */
case class Supervisor(project: Project) {

  private[this] val repo = GithubUtil.parseUri(project.uri).right.getOrElse {
    sys.error(s"Project id[${project.id}] uri[${project.uri}]: Could not parse")
  }

  private[this] val log = EventLog.withSystemUser(project, "Supervisor")

  private[this] val InitialTag = "0.0.1"
  
  def getClient(): Option[Client] = {
    GithubHelper.apiClientFromUser(project.user.id) match {
      case None => {
        Logger.warn(s"Could not get github client for user[${project.user.id}]")
        None
      }
      case Some(client) => {
        Some(client)
      }
    }
  }

  /**
    * Look up the sha for the master branch from github, and record it
    * in the shas table. Returns true if a new sha was found, false
    * otherwise.
    */
  def captureMasterSha(
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[Boolean] = {
    getClient match {
      case None => {
        Future { false }
      }
      case Some(client) => {
        val existing = ShasDao.findByProjectIdAndMaster(Authorization.All, project.id).map(_.hash)

        client.refs.getByRef(repo.owner, repo.project, "heads/master").map { master =>
          val updated = ShasDao.upsertMaster(UsersDao.systemUser, project.id, master.`object`.sha)
          val changed = existing != Some(updated.hash)
          changed
        }
      }
    }
  }

  def tagIfNeeded(
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[Boolean] = {
    ShasDao.findByProjectIdAndMaster(Authorization.All, project.id).map(_.hash) match {
      case None => {
        Future { false }
      }
      case Some(master) => {
        computeNextTag(master).map {
          case None => {
            false
          }
          case Some(tag) => {
            println("create tag[%s] for sha[%s]".format(tag, master))
            true
          }
        }
      }
    }
  }

  /**
    * Computes the next tag. If there are no tags in this repo,
    * returns InitialVersion. Otherwise returns the next micro version
    * to use as the tag.
    */
  private[this] def computeNextTag(
    master: String
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[Option[String]] = {
    getClient match {
      case None => {
        Future { None }
      }

      case Some(client) => {

        client.tags.get(repo.owner, repo.project).map { tags =>

          tags.
            filter { t => Semver.isSemver(t.name) }.
            sortBy { t => Semver.parse(t.name) }.
            reverse.
            headOption match {
              case None => {
                // Apply first tag
                Some(InitialTag)
              }
              case Some(tag) => {
                Some(tag.commit.sha) == master match {
                  case true => {
                    // Latest tag already points to head of master
                    None
                  }
                  case false => {
                    Some(
                      Semver.parse(tag.name).map(_.next.toString).getOrElse {
                        sys.error(s"Failed to parse tag[${tag.name}]")
                      }
                    )
                  }
                }
              }
            }
        }
      }
    }
  }

}
