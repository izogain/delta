package db

import io.flow.delta.v0.models.{Project, State, StateForm, Version}
import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ProjectStatesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  def createProjectDesiredState(
    project: Project = createProject(),
    form: StateForm = createStateForm()
  ): State = {
    rightOrErrors(ProjectDesiredStatesDao.create(systemUser, project, form))
  }

  def createStateForm(): StateForm = {
    StateForm(
      versions = Seq(
        Version(name = "0.0.1", instances = 3),
        Version(name = "0.0.2", instances = 2)
      )
    )
  }

  "create desired" in {
    val project = createProject()
    val state = rightOrErrors(ProjectDesiredStatesDao.create(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "create actual" in {
    val project = createProject()
    val state = rightOrErrors(ProjectLastStatesDao.create(systemUser, project, createStateForm()))
    state.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "upsert" in {
    val project = createProject()
    val state = rightOrErrors(ProjectDesiredStatesDao.upsert(systemUser, project, createStateForm()))
    val second = rightOrErrors(ProjectDesiredStatesDao.upsert(systemUser, project, createStateForm()))
    second.versions.map(_.name) must be(Seq("0.0.1", "0.0.2"))
    state.versions.map(_.instances) must be(Seq(3, 2))
  }

  "delete" in {
    val project = createProject()
    val state = createProjectDesiredState(project)
    ProjectDesiredStatesDao.delete(systemUser, project)
    ProjectDesiredStatesDao.findByProjectId(Authorization.All, project.id) must be(None)
  }

  "saving prunes records w/ zero instances" in {
    val form = StateForm(
      versions = Seq(
        Version(name = "0.0.1", instances = 0),
        Version(name = "0.0.2", instances = 2)
      )
    )
    
    val project = createProject()
    val state = rightOrErrors(ProjectDesiredStatesDao.create(systemUser, project, form))
    state.versions.map(_.name) must be(Seq("0.0.2"))
    state.versions.map(_.instances) must be(Seq(2))
  }

}
