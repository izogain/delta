package controllers

import io.flow.delta.v0.Client
import io.flow.delta.v0.models.Organization
import io.flow.delta.www.lib.{DeltaClientProvider, Section, UiData}
import io.flow.play.clients.UserTokensClient
import io.flow.common.v0.models.User
import io.flow.play.controllers.IdentifiedController
import scala.concurrent.{ExecutionContext, Future}
import play.api._
import play.api.i18n._
import play.api.mvc._

object Helpers {

  def userFromSession(
    userTokensClient: UserTokensClient,
    session: play.api.mvc.Session
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): scala.concurrent.Future[Option[io.flow.common.v0.models.User]] = {
    session.get("user_id") match {
      case None => {
        scala.concurrent.Future { None }
      }
      case Some(userId) => {
        userTokensClient.getUserByToken(userId)
      }
    }
  }

}

abstract class BaseController(
  val userTokensClient: UserTokensClient,
  val deltaClientProvider: DeltaClientProvider
) extends Controller
    with IdentifiedController
    with I18nSupport
{

  def section: Option[Section]

  override def unauthorized[A](request: Request[A]): Result = {
    Redirect(routes.LoginController.index(return_url = Some(request.path))).flashing("warning" -> "Please login")
  }

  def withOrganization[T](
    request: IdentifiedRequest[T],
    key: String
  ) (
    f: Organization => Future[Result]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ) = {
    deltaClient(request).organizations.get(key = Some(key), limit = 1).flatMap { organizations =>
      organizations.headOption match {
        case None => Future {
          Redirect(routes.ApplicationController.index()).flashing("warning" -> s"Organization not found")
        }
        case Some(org) => {
          f(org)
        }
      }
    }
  }

  def organizations[T](
    request: IdentifiedRequest[T]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[Seq[Organization]] = {
    deltaClient(request).organizations.get(
      userId = Some(request.user.id),
      limit = 100
    )
  }

  override def user(
    session: play.api.mvc.Session,
    headers: play.api.mvc.Headers,
    path: String,
    queryString: Map[String, Seq[String]]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): scala.concurrent.Future[Option[io.flow.common.v0.models.User]] = {
    Helpers.userFromSession(userTokensClient, session)
  }

  def uiData[T](request: IdentifiedRequest[T]): UiData = {
    UiData(
      requestPath = request.path,
      user = Some(request.user),
      section = section
    )
  }

  def uiData[T](request: AnonymousRequest[T], user: Option[User]): UiData = {
    UiData(
      requestPath = request.path,
      user = user,
      section = section
    )
  }

  def deltaClient[T](request: IdentifiedRequest[T]): Client = {
    deltaClientProvider.newClient(user = Some(request.user))
  }

}
