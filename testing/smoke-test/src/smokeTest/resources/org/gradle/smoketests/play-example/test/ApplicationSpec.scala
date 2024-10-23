import com.google.common.collect.Lists
import org.apache.commons.lang.StringUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends PlaySpec with GuiceOneAppPerSuite {

  "Application" should {

    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/boum")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe Status.OK
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
