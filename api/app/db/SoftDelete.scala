package db

import anorm._
import play.api.db._
import play.api.Play.current

object SoftDelete {

  private[this] val Query = """
    update %s set deleted_at=now(), updated_by_user_id = {updated_by_user_id} where id = {id}
  """

  def delete(tableName: String, deletedById: String, id: String) {
    DB.withConnection { implicit c =>
      delete(c, tableName, deletedById, id)
    }
  }

  def delete(
    implicit c: java.sql.Connection,
    tableName: String, deletedById: String, id: String
  ) {
    SQL(Query.format(tableName)).on(
      'id -> id,
      'updated_by_user_id -> deletedById
    ).execute()
  }

}
