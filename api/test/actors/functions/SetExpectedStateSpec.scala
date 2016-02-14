package io.flow.delta.actors.functions

import io.flow.delta.actors.SupervisorResult
import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

class SetExpectedStateSpec extends PlaySpec with OneAppPerSuite with db.Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "no-op if no tags" in {
    val project = createProject()
    SetExpectedState(project).run() must be(SupervisorResult.NoChange("Project does not have any tags"))
  }

  // TODO: Need to disable actors for this test
  "sets expected state to latest tag" in {
    val project = createProject()
    val tag1 = createTag(createTagForm(project).copy(name = "0.0.1"))
    SetExpectedState(project).run() must be(SupervisorResult.Change("Expected state changed to: 0.0.1: 2 instances"))

    val tag2 = createTag(createTagForm(project).copy(name = "0.0.2"))
    SetExpectedState(project).run() must be(SupervisorResult.Change("Expected state changed to: 0.0.2: 2 instances"))

    // No-op if no change
    SetExpectedState(project).run() must be(SupervisorResult.NoChange("Expected state remains: 0.0.2: 2 instances"))
  }

}
