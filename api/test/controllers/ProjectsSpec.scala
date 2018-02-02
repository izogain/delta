package controllers

import java.util.UUID

import play.api.test._

class ProjectsSpec extends MockClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val project1 = createProject(org)()
  lazy val project2 = createProject(org)()

  "GET /projects by id" in new WithServer(port=port) {
    await(
      identifiedClient().projects.get(id = Some(Seq(project1.id)))
    ).map(_.id) must be(
      Seq(project1.id)
    )

    await(
      identifiedClient().projects.get(id = Some(Seq(UUID.randomUUID.toString)))
    ).map(_.id) must be(
      Nil
    )
  }

  "GET /projects by name" in new WithServer(port=port) {
    await(
      identifiedClient().projects.get(name = Some(project1.name))
    ).map(_.name) must be(
      Seq(project1.name)
    )

    await(
      identifiedClient().projects.get(name = Some(project1.name.toUpperCase))
    ).map(_.name) must be(
      Seq(project1.name)
    )

    await(
      identifiedClient().projects.get(name = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /projects/:id" in new WithServer(port=port) {
    await(identifiedClient().projects.getById(project1.id)).id must be(project1.id)
    await(identifiedClient().projects.getById(project2.id)).id must be(project2.id)

    expectNotFound {
      identifiedClient().projects.getById(UUID.randomUUID.toString)
    }
  }

  "POST /projects" in new WithServer(port=port) {
    val form = createProjectForm(org)
    val project = await(identifiedClient().projects.post(form))
    project.name must be(form.name)
    project.scms must be(form.scms)
    project.uri must be(form.uri)
  }

  "POST /projects validates duplicate name" in new WithServer(port=port) {
    expectErrors(
      identifiedClient().projects.post(createProjectForm(org).copy(name = project1.name))
    ).errors.map(_.message) must be(
      Seq("Project with this name already exists")
    )
  }

  "PUT /projects/:id" in new WithServer(port=port) {
    val form = createProjectForm(org)
    val project = createProject(org)(form)
    val newUri = "http://github.com/mbryzek/test"
    await(identifiedClient().projects.putById(project.id, form.copy(uri = newUri)))
    await(identifiedClient().projects.getById(project.id)).uri must be(newUri)
  }

  "DELETE /projects" in new WithServer(port=port) {
    val project = createProject(org)()
    await(
      identifiedClient().projects.deleteById(project.id)
    ) must be(())

    expectNotFound(
      identifiedClient().projects.getById(project.id)
    )

    expectNotFound(
      identifiedClient().projects.deleteById(project.id)
    )
  }

}
