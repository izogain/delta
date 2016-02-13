package io.flow.delta.actors.functions

import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}

import io.flow.delta.api.lib.Semver
import io.flow.github.v0.Client 
import io.flow.github.v0.models.TagSummary
import io.flow.postgresql.Authorization
import db.{ProjectsDao, ShasDao, UsersDao}
import io.flow.delta.api.lib.{EventLog, GithubUtil, GithubHelper, Repo}
import io.flow.delta.v0.models.Project
import play.api.Logger
import play.libs.Akka
import akka.actor.Actor
import scala.concurrent.Future

/**
  * If there is no tag pointing to the master sha, creates a tag in
  * github and records it here.
  */
object TagIfNeeded extends SupervisorFunction {

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    TagIfNeeded(project).run
  }

}

case class TagIfNeeded(project: Project) extends Github {

  private[this] case class Tag(semver: Semver, sha: String)

  private[this] val repo = GithubUtil.parseUri(project.uri).right.getOrElse {
    sys.error(s"Project id[${project.id}] uri[${project.uri}]: Could not parse")
  }

  val InitialTag = "0.0.1"

  def run(
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    ShasDao.findByProjectIdAndMaster(Authorization.All, project.id).map(_.hash) match {

      case None => {
        Future {
          SupervisorResult.NoChange("Shas table does not have an entry for master branch")
        }
      }

      case Some(master) => {
        withGithubClient(project.user.id) { client =>
          client.tags.get(repo.owner, repo.project).map { tags =>
            latest(tags) match {
              case None => {
                createTag(InitialTag, master)
                SupervisorResult.Change(s"Creating initial tag $InitialTag for sha[$master]")
              }
              case Some(tag) => {
                Some(tag.sha) == master match {
                  case true => {
                    SupervisorResult.NoChange(s"Latest tag[${tag.semver}] already points to master[${master}]")
                  }
                  case false => {
                    val nextTag = tag.semver.next.toString
                    createTag(nextTag, master)
                    SupervisorResult.Change(s"Creating tag $nextTag for sha[$master]")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private[this] def createTag(name: String, sha: String) {
    println(s"createTag($name, $sha)")
  }

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
    * Given a list of tag summaries from github, returns the latest
    * semver tag along with its sha
    */
  private[this] def latest(tags: Seq[TagSummary]): Option[Tag] = {
    tags.
      flatMap { t =>
        Semver.parse(t.name).map( semvar => Tag(semvar, t.commit.sha) )
      }.
      sortBy { _.semver }.
      reverse.
      headOption
  }

}
