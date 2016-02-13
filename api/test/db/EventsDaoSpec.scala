package db

import io.flow.postgresql.Authorization
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class EventsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "create" in {
    val project = createProject()
    val id = EventsDao.create(systemUser, project.id, "started", "test", ex = None)
    val event = EventsDao.findById(id).getOrElse {
      sys.error("Failed to create event")
    }
    event.action must be("started")
    event.summary must be("test")
    event.error must be(None)
  }

  "findById" in {
    val event = createEvent()
    EventsDao.findById(event.id).map(_.id) must be(
      Some(event.id)
    )

    EventsDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val event1 = createEvent()
    val event2 = createEvent()

    EventsDao.findAll(ids = Some(Seq(event1.id, event2.id))).map(_.id).sorted must be(
      Seq(event1.id, event2.id).sorted
    )

    EventsDao.findAll(ids = Some(Nil)) must be(Nil)
    EventsDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    EventsDao.findAll(ids = Some(Seq(event1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(event1.id))
  }

  "findAll by projectId" in {
    val project1 = createProject()
    val project2 = createProject()

    val event1 = createEvent(project1)
    val event2 = createEvent(project2)

    EventsDao.findAll(projectId = Some(project1.id)).map(_.id).sorted must be(
      Seq(event1.id)
    )

    EventsDao.findAll(projectId = Some(project2.id)).map(_.id).sorted must be(
      Seq(event2.id)
    )

    EventsDao.findAll(projectId = Some(createTestKey())) must be(Nil)
  }

}
