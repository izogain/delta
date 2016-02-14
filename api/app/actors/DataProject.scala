package io.flow.delta.actors

import db.ProjectsDao
import io.flow.delta.api.lib.{GithubUtil, Repo}
import io.flow.delta.v0.models.Project
import io.flow.postgresql.Authorization
import play.api.Logger

trait DataProject {

  private[this] var dataProject: Option[Project] = None

  /**
    * Looks up the project with the specified ID, setting the local
    * dataProject var to that project
    */
  def setDataProject(id: String) {
    dataProject = ProjectsDao.findById(Authorization.All, id)
    if (dataProject.isEmpty) {
      Logger.warn(s"Could not find project with id[$id]")
    }
  }

  /**
    * Invokes the specified function w/ the current project, but only
    * if we have a project set.
    */
  def withProject[T](f: Project => T): Option[T] = {
    dataProject.map { f(_) }
  }

  /**
    * Invokes the specified function w/ the current project, parsed
    * into a Repo, but only if we have a project set and it parses
    * into a valid Repo.
    */
  def withRepo[T](f: Repo => T): Option[T] = {
    dataProject.flatMap { project =>
      GithubUtil.parseUri(project.uri) match {
        case Left(error) => {
          Logger.warn(s"Cannot parse repo from project id[${project.id}] uri[${project.uri}]: $error")
          None
        }
        case Right(repo) => {
          Some(
            f(repo)
          )
        }
      }
    }
  }

}
