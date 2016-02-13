package controllers

import io.flow.delta.v0.{Authorization, Client}
import io.flow.delta.v0.models.SettingsForm

import play.api.libs.ws._
import play.api.test._

class SettingsSpec extends PlaySpecification with MockClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "GET /projects/:id/settings for a new project" in new WithServer(port=port) {
    val project = createProject(org)

    val settings = await(
      client.projects.getSettingsById(project.id)
    )

    settings.autoTag must beEqualTo(true)
  }

  "PUT /projects/:id/settings for a new project" in new WithServer(port=port) {
    val project = createProject(org)

    val settings = await(
      client.projects.putSettingsById(project.id, SettingsForm(autoTag = Some(false)))
    )
    settings.autoTag must beEqualTo(false)

    val settings2 = await(
      client.projects.putSettingsById(project.id, SettingsForm(autoTag = None))
    )
    settings2.autoTag must beEqualTo(false)

    val settings3 = await(
      client.projects.putSettingsById(project.id, SettingsForm(autoTag = Some(true)))
    )
    settings3.autoTag must beEqualTo(true)
  }
}
