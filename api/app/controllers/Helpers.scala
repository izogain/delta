package controllers

import db.{Authorization, OrganizationsDao, ProjectsDao, UsersDao}
import io.flow.delta.v0.models.{Organization, Project}
import io.flow.common.v0.models.User
import play.api.mvc.{Result, Results}

trait Helpers {

  def withOrganization(user: User, id: String)(
    f: Organization => Result
  ) = {
    OrganizationsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(organization) => {
        f(organization)
      }
    }
  }

  def withProject(user: User, id: String)(
    f: Project => Result
  ): Result = {
    ProjectsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(project) => {
        f(project)
      }
    }
  }

  def withUser(id: String)(
    f: User => Result
  ) = {
    UsersDao.findById(id) match {
      case None => {
        Results.NotFound
      }
      case Some(user) => {
        f(user)
      }
    }
  }

}
