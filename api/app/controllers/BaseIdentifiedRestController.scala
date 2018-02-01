package controllers

import io.flow.play.controllers.{FlowController, IdentifiedRequest}
import io.flow.postgresql.Authorization

trait BaseIdentifiedRestController extends FlowController with Helpers {

  def authorization[T](request: IdentifiedRequest[T]): Authorization = {
    Authorization.User(request.user.id)
  }

}
