package db

import io.flow.delta.v0.models.{MembershipForm, Organization, OrganizationForm, Role}
import io.flow.postgresql.{Authorization, Query, OrderBy}
import io.flow.play.util.{IdGenerator, Random, UrlKey}
import io.flow.common.v0.models.User
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

object OrganizationsDao {

  val DefaultUserNameLength = 8

  private[this] val BaseQuery = Query(s"""
    select organizations.id,
           organizations.user_id
      from organizations
  """)

  private[this] val InsertQuery = """
    insert into organizations
    (id, user_id, updated_by_user_id)
    values
    ({id}, {user_id}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update organizations
       set updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[this] val InsertUserOrganizationQuery = """
    insert into user_organizations
    (id, user_id, organization_id, updated_by_user_id)
    values
    ({id}, {user_id}, {organization_id}, {updated_by_user_id})
  """

  private[this] val random = Random()
  private[this] val urlKey = UrlKey(minKeyLength = 3)

  private[db] def validate(
    form: OrganizationForm,
    existing: Option[Organization] = None
  ): Seq[String] = {
    if (form.id.trim == "") {
      Seq("Id cannot be empty")

    } else {
      urlKey.validate(form.id.trim) match {
        case Nil => {
          OrganizationsDao.findById(Authorization.All, form.id) match {
            case None => Seq.empty
            case Some(p) => {
              Some(p.id) == existing.map(_.id) match {
                case true => Nil
                case false => Seq("Organization with this id already exists")
              }
            }
          }
        }
        case errors => errors
      }
    }
  }

  def create(createdBy: User, form: OrganizationForm): Either[Seq[String], Organization] = {
    validate(form) match {
      case Nil => {
        val id = DB.withTransaction { implicit c =>
          create(c, createdBy, form)
        }
        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create organization")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  private[this] def create(implicit c: java.sql.Connection, createdBy: User, form: OrganizationForm): String = {
    val id = IdGenerator("org").randomId()

    SQL(InsertQuery).on(
      'id -> id,
      'user_id -> createdBy.id,
      'updated_by_user_id -> createdBy.id
    ).execute()

    MembershipsDao.create(
      c,
      createdBy,
      id,
      createdBy.id,
      Role.Admin
    )

    id
  }

  def update(createdBy: User, organization: Organization, form: OrganizationForm): Either[Seq[String], Organization] = {
    validate(form, Some(organization)) match {
      case Nil => {
        DB.withConnection { implicit c =>
          SQL(UpdateQuery).on(
            'id -> organization.id,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        Right(
          findById(Authorization.All, organization.id).getOrElse {
            sys.error("Failed to create organization")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def softDelete(deletedBy: User, organization: Organization) {
    SoftDelete.delete("organizations", deletedBy.id, organization.id)
  }

  def findById(auth: Authorization, id: String): Option[Organization] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    forUserId: Option[String] = None,
    isDeleted: Option[Boolean] = Some(false),
    orderBy: OrderBy = OrderBy("organizations.id, -organizations.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Organization] = {
    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "organizations",
        auth = Filters(auth).organizations("organizations.id"),
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        isDeleted = isDeleted,
        limit = limit,
        offset = offset
      ).
        and(
          userId.map { id =>
            "organizations.id in (select organization_id from memberships where deleted_at is null and user_id = {user_id})"
          }
        ).bind("user_id", userId).
        and(
          forUserId.map { id =>
            "organizations.id in (select organization_id from user_organizations where deleted_at is null and user_id = {for_user_id})"
          }
        ).bind("for_user_id", forUserId).
        as(
          io.flow.delta.v0.anorm.parsers.Organization.parser().*
        )
    }
  }

}
