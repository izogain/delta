package io.flow.delta.actors.functions

import db.{ProjectExpectedStatesDao, TagsDao, UsersDao}
import io.flow.delta.actors.{SupervisorFunction, SupervisorResult}
import io.flow.delta.api.lib.StateFormatter
import io.flow.delta.v0.models.{Project, Settings, StateForm, Version}
import io.flow.postgresql.{Authorization, OrderBy}
import scala.concurrent.Future

/**
  * For projects that have auto deploy turned on, we set the expected
  * state to 100% of traffic on the latest tag.
  */
object SetExpectedState extends SupervisorFunction {

  val DefaultNumberInstances = 2

  override def run(
    project: Project
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[SupervisorResult] = {
    Future {
      SetExpectedState(project).run
    }
  }

  override def isEnabled(settings: Settings) = settings.setExpectedState
}

case class SetExpectedState(project: Project) extends Github {

  def run(): SupervisorResult = {
    TagsDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      orderBy = OrderBy("-tags.created_at"),
      limit = 1
    ).headOption match {
      case None => {
        SupervisorResult.NoChange("Project does not have any tags")
      }

      case Some(latestTag) => {
        ProjectExpectedStatesDao.findByProjectId(Authorization.All, project.id) match {
          case None => {
            setVersions(Seq(Version(latestTag.name, instances = SetExpectedState.DefaultNumberInstances)))
          }
          case Some(state) => {
            val instances: Long = state.versions.headOption.map(_.instances).getOrElse {
              SetExpectedState.DefaultNumberInstances
            }
            val targetVersions = Seq(Version(latestTag.name, instances = instances))

            if (state.versions == targetVersions) {
              SupervisorResult.NoChange("Expected state remains: " + StateFormatter.label(targetVersions))
            } else {
              setVersions(targetVersions)
            }
          }
        }
      }
    }
  }

  def setVersions(versions: Seq[Version]): SupervisorResult = {
    assert(!versions.isEmpty, "Must have at least one version")
    ProjectExpectedStatesDao.upsert(
      UsersDao.systemUser,
      project,
      StateForm(
        versions = versions
      )
    )
    SupervisorResult.Change("Expected state changed to: " + StateFormatter.label(versions))
  }

}
