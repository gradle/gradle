import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerTest

/**
 * https://github.com/playframework/play-scala-starter-example/blob/2.6.x/test/BrowserSpec.scala
 */
class IntegrationSpec extends PlaySpec
  with OneBrowserPerTest
  with GuiceOneServerPerTest
  with HtmlUnitFactory
  with ServerProvider {

  "Application" should {

    "work from within a browser" in {

      go to("http://localhost:" + port)

      pageSource must include ("Your new application is ready.")
    }
  }
}
