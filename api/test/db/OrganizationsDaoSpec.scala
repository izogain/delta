package db

import io.flow.postgresql.Authorization
import io.flow.delta.v0.models.Role
import io.flow.common.v0.models.Name
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class OrganizationsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "create" in {
    val form = createOrganizationForm()
    val organization = OrganizationsDao.create(systemUser, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    organization.id must be(form.id)
  }

  "creation users added as admin of org" in {
    val user = createUser()
    val form = createOrganizationForm()
    val org = OrganizationsDao.create(user, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    val membership = MembershipsDao.findByOrganizationIdAndUserId(Authorization.All, org.id, user.id).getOrElse {
      sys.error("Failed to create membership record")
    }
    membership.role must be(Role.Admin)
  }

  "delete" in {
    val org = createOrganization()
    OrganizationsDao.delete(systemUser, org)
    OrganizationsDao.findById(Authorization.All, org.id) must be(None)
  }

  "findById" in {
    val organization = createOrganization()
    OrganizationsDao.findById(Authorization.All, organization.id).map(_.id) must be(
      Some(organization.id)
    )

    OrganizationsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val organization1 = createOrganization()
    val organization2 = createOrganization()

    OrganizationsDao.findAll(Authorization.All, ids = Some(Seq(organization1.id, organization2.id))).map(_.id).sorted must be(
      Seq(organization1.id, organization2.id).sorted
    )

    OrganizationsDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    OrganizationsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    OrganizationsDao.findAll(Authorization.All, ids = Some(Seq(organization1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(organization1.id))
  }

  "validate" must {

    "keep key url friendly" in {
      OrganizationsDao.validate(createOrganizationForm().copy(id = "flow commerce")) must be(
        Seq("Id must be in all lower case and contain alphanumerics only (-, _, and . are supported). A valid id would be: flow-commerce")
      )
    }

  }

  "authorization for organizations" in {
    val user = createUser()
    val org = createOrganization(user = user)

    OrganizationsDao.findAll(Authorization.PublicOnly, id = Some(org.id)) must be(Nil)
    OrganizationsDao.findAll(Authorization.All, id = Some(org.id)).map(_.id) must be(Seq(org.id))
    OrganizationsDao.findAll(Authorization.Organization(org.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    OrganizationsDao.findAll(Authorization.Organization(createOrganization().id), id = Some(org.id)) must be(Nil)
    OrganizationsDao.findAll(Authorization.User(user.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    OrganizationsDao.findAll(Authorization.User(createUser().id), id = Some(org.id)) must be(Nil)
  }

}
