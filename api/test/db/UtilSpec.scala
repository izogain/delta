package db

import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

class UtilSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "generateVersionSortKey" in {
    Util.generateVersionSortKey("0.0.1") must be("9:10000.10000.10001")
    Util.generateVersionSortKey("0") must be("1:0")
    Util.generateVersionSortKey("other") must be("1:other")
  }

}
