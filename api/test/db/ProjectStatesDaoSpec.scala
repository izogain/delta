package db

import io.flow.delta.v0.models.{Project, State, StateForm, Version}
import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ProjectExpectedStatesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  def createProjectExpectedState(
    project: Project = createProject(),
    form: StateForm = createStateForm()
  ): State = {
    rightOrErrors(ProjectExpectedStatesDao.create(systemUser, project, form))
  }

  def createStateForm(): StateForm = {
    StateForm(
      versions = Seq(
        Version(name = "0.0.1", instances = 3),
        Version(name = "0.0.2", instances = 2)
      )
    )
  }

  "create expected" in {
    val project = createProject()
    val state = rightOrErrors(ProjectExpectedStatesDao.create(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "create actual" in {
    val project = createProject()
    val state = rightOrErrors(ProjectActualStatesDao.create(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "upsert" in {
    val project = createProject()
    val state = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    val second = rightOrErrors(ProjectExpectedStatesDao.upsert(systemUser, project, createStateForm()))
    second.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "delete" in {
    val project = createProject()
    val state = createProjectExpectedState(project)
    ProjectExpectedStatesDao.delete(systemUser, project)
    ProjectExpectedStatesDao.findByProjectId(Authorization.All, project.id) must be(None)
  }

}
