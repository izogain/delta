package controllers

import io.flow.delta.v0.Client
import io.flow.delta.v0.models.Organization
import io.flow.delta.www.lib.{DeltaClientProvider, Section, UiData}
import io.flow.common.v0.models.UserReference
import io.flow.play.controllers.IdentifiedController
import io.flow.play.util.AuthData
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import play.api.i18n._
import play.api.mvc._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object Helpers {

  def userFromHeaders(headers: play.api.mvc.Headers)(implicit ec: scala.concurrent.ExecutionContext): Option[AuthData] = {
    // hack - how to get this without using the mvc.Session?
    headers.get("COOKIE") match {
      case None => None
      case Some(cookie) => {
        val pattern = "PLAY_SESSION=\\w+-user_id=([\\w-]+)".r
        cookie match {
          case pattern(user) => {
            Some(AuthData(createdAt = new DateTime(), user = UserReference(id = user), organization = None))
          }
          case _ => {
            None
          }
        }
      }
    }
  }

  def userFromSession(
    tokenClient: io.flow.token.v0.interfaces.Client,
    session: play.api.mvc.Session
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): scala.concurrent.Future[Option[UserReference]] = {
    session.get("user_id") match {
      case None => {
        scala.concurrent.Future { None }
      }
      case Some(userId) => {
        Future { Some(UserReference(id = userId)) }
      }
    }
  }

}

abstract class BaseController(
  val tokenClient: io.flow.token.v0.interfaces.Client,
  val deltaClientProvider: DeltaClientProvider
) extends Controller
    with IdentifiedController
    with I18nSupport
{

  private[this] lazy val client = deltaClientProvider.newClient(user = None)

  def section: Option[Section]

  override def unauthorized[A](request: Request[A]): Result = {
    Redirect(routes.LoginController.index(return_url = Some(request.path))).flashing("warning" -> "Please login")
  }

  def withOrganization[T](
    request: IdentifiedRequest[T],
    id: String
  ) (
    f: Organization => Future[Result]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ) = {
    deltaClient(request).organizations.get(id = Some(Seq(id)), limit = 1).flatMap { organizations =>
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

  override def auth(headers: play.api.mvc.Headers)(implicit ec: scala.concurrent.ExecutionContext): Option[AuthData] = {
    Helpers.userFromHeaders(headers)
  }

  def uiData[T](
    request: IdentifiedRequest[T]
  ) (
    implicit ec: ExecutionContext
  ): UiData = {
    val user = Await.result(
      client.users.get(id = Some(request.user.id)),
      Duration(1, "seconds")
    ).headOption

    UiData(
      requestPath = request.path,
      user = user,
      section = section
    )
  }

  def uiData[T](
    request: AnonymousRequest[T], user: Option[UserReference]
  ) (
    implicit ec: ExecutionContext
  ): UiData = {
    val userReferenceOption = request.user

    val user = userReferenceOption.flatMap { ref =>
      Await.result(
        client.users.get(id = Some(ref.id)),
        Duration(1, "seconds")
      ).headOption
    }

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
