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

    settings.syncMasterSha must beEqualTo(true)
    settings.tagMaster must beEqualTo(true)
    settings.setExpectedState must beEqualTo(true)
  }

  "PUT /projects/:id/settings for a new project" in new WithServer(port=port) {
    val project = createProject(org)

    val settings = await(
      client.projects.putSettingsById(project.id, SettingsForm(
        syncMasterSha = Some(false),
        tagMaster = Some(false),
        setExpectedState = Some(false)
      ))
    )
    settings.syncMasterSha must beEqualTo(false)
    settings.tagMaster must beEqualTo(false)
    settings.setExpectedState must beEqualTo(false)

    val settings2 = await(
      client.projects.putSettingsById(project.id, SettingsForm(
        syncMasterSha = None,
        tagMaster = None,
        setExpectedState = None
      ))
    )
    settings2.syncMasterSha must beEqualTo(false)
    settings2.tagMaster must beEqualTo(false)
    settings2.setExpectedState must beEqualTo(false)

    val settings3 = await(
      client.projects.putSettingsById(project.id, SettingsForm(
        syncMasterSha = Some(true),
        tagMaster = Some(true),
        setExpectedState = Some(true)
      ))
    )
    settings3.syncMasterSha must beEqualTo(true)
    settings3.tagMaster must beEqualTo(true)
    settings3.setExpectedState must beEqualTo(true)
  }
}
