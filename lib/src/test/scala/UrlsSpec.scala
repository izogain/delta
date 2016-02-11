package io.flow.delta.lib

import io.flow.delta.v0.models.ItemSummaryUndefinedType
import org.scalatest.{FunSpec, Matchers}

class UrlsSpec extends FunSpec with Matchers with Factories {

  private[this] lazy val urls = Urls(wwwHost = "http://localhost")

  it("www") {
    urls.www("/foo") should be("http://localhost/foo")
  }

  it("itemSummary") {
    val project = makeProjectSummary()
    urls.itemSummary(project) should be(s"/projects/${project.id}")

    urls.itemSummary(ItemSummaryUndefinedType("other")) should be("#")
  }

}
