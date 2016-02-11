package db

object Util {

  def trimmedString(value: Option[String]): Option[String] = {
    value match {
      case None => None
      case Some(v) => {
        v.trim match {
          case "" => None
          case trimmed => Some(trimmed)
        }
      }
    }
  }

}
