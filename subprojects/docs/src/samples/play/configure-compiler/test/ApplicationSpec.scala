import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._

import org.apache.commons.lang.StringUtils
import com.google.common.collect.Lists

/**
 * https://github.com/playframework/play-scala-starter-example/blob/2.6.x/test/FunctionalSpec.scala
 */
class ApplicationSpec extends PlaySpec with GuiceOneAppPerSuite{

  "Application" should {
    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/boum")) mustBe Some(NOT_FOUND)
    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }

    "tests can use commons-lang play dependency" in {
      StringUtils.reverse("foobar") mustBe "raboof"
    }

    "tests can use guava play-test dependency" in {
      Lists.newArrayList("foo", "bar").size() mustBe 2
    }
  }
}
