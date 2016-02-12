package db

import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ShasDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val project = createProject()

  "create" in {
    val form = createShaForm(project).copy(branch = "master", sha = "foo")
    val commit = ShasDao.create(systemUser, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    commit.project.id must be(project.id)
    commit.branch must be("master")
    commit.sha must be("foo")
  }

  /*
  "creation users added as admin of org" in {
    val user = createUser()
    val form = createShaForm()
    val org = ShasDao.create(user, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    val membership = MembershipsDao.findByShaIdAndUserId(Authorization.All, org.id, user.id).getOrElse {
      sys.error("Failed to create membership record")
    }
    membership.role must be(Role.Admin)
  }

  "delete" in {
    val org = createSha()
    ShasDao.delete(systemUser, org)
    ShasDao.findById(Authorization.All, org.id) must be(None)
  }

  "findById" in {
    val sha = createSha()
    ShasDao.findById(Authorization.All, sha.id).map(_.id) must be(
      Some(sha.id)
    )

    ShasDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val sha1 = createSha()
    val sha2 = createSha()

    ShasDao.findAll(Authorization.All, ids = Some(Seq(sha1.id, sha2.id))).map(_.id).sorted must be(
      Seq(sha1.id, sha2.id).sorted
    )

    ShasDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    ShasDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    ShasDao.findAll(Authorization.All, ids = Some(Seq(sha1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(sha1.id))
  }

  "validate" must {

    "keep key url friendly" in {
      ShasDao.validate(createShaForm().copy(id = "flow commerce")) must be(
        Seq("Id must be in all lower case and contain alphanumerics only (-, _, and . are supported). A valid id would be: flow-commerce")
      )
    }

  }

  "authorization for shas" in {
    val user = createUser()
    val org = createSha(user = user)

    ShasDao.findAll(Authorization.PublicOnly, id = Some(org.id)) must be(Nil)
    ShasDao.findAll(Authorization.All, id = Some(org.id)).map(_.id) must be(Seq(org.id))
    ShasDao.findAll(Authorization.Sha(org.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    ShasDao.findAll(Authorization.Sha(createSha().id), id = Some(org.id)) must be(Nil)
    ShasDao.findAll(Authorization.User(user.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    ShasDao.findAll(Authorization.User(createUser().id), id = Some(org.id)) must be(Nil)
  }
   */
}
