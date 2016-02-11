package io.flow.delta.actors

import io.flow.delta.v0.models.{Publication, SubscriptionForm}
import io.flow.common.v0.models.User
import io.flow.play.actors.Util
import db.{OrganizationsDao, SubscriptionsDao, UserIdentifiersDao, UsersDao}
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object UserActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Created extends Message
  }

}

class UserActor extends Actor with Util {

  var dataUser: Option[User] = None

  def receive = {

    case m @ UserActor.Messages.Data(id) => withVerboseErrorHandler(m.toString) {
      dataUser = UsersDao.findById(id)
    }

    case m @ UserActor.Messages.Created => withVerboseErrorHandler(m.toString) {
      dataUser.foreach { user =>
        // This method will force create an identifier
        UserIdentifiersDao.latestForUser(MainActor.SystemUser, user)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
