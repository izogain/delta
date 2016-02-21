package io.flow.delta.actors

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActorFactory[DockerHubActor, DockerHubActor.Factory]
    bindActorFactory[ProjectActor, ProjectActor.Factory]
    bindActor[MainActor]("main-actor")
  }
}
