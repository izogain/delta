package io.flow.delta.www.lib

import io.flow.delta.v0.models.Project
import io.flow.common.v0.models.User

object Permissions {

  object Organization {

    def edit(user: Option[User]): Boolean = !user.isEmpty
    def delete(user: Option[User]): Boolean = edit(user)

  }

  object Project {

    def edit(user: Option[User]): Boolean = !user.isEmpty
    def delete(project: Project, user: Option[User]): Boolean = edit(user)

  }

}
