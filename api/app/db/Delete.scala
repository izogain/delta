package db

import anorm._
import play.api.db._
import play.api.Play.current

object Delete {

  private[this] val Query = """
    select util.delete_by_id({user_id}, {table}, {id})
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
    SQL(Query).on(
      'id -> id,
      'table -> tableName,
      'user_id -> deletedById
    ).execute()
  }

}
