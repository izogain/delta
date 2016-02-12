package io.flow.delta.lib

import io.flow.delta.v0.models.{ItemSummary, ItemSummaryUndefinedType, OrganizationSummary, ProjectSummary}
import io.flow.play.util.{IdGenerator, Random}
import org.joda.time.DateTime

trait Factories {

  val idGenerator = IdGenerator("tst")
  val random = Random()

  def makeName(): String = {
    s"Z Test ${random.alpha(20)}"
  }

  def makeKey(): String = {
    "z-test-${random.alphaNumeric(20)}"
  }

  def makeProjectSummary(
    id: String = idGenerator.randomId(),
    name: String = makeName()
  ) = ProjectSummary(
    id = id,
    organization = makeOrganizationSummary(),
    name = name
  )

  def makeOrganizationSummary(
    id: String = makeKey()
  ) = OrganizationSummary(
    id = id
  )

}
