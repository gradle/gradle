package controllers.submodule

import javax.inject._
import play.api._
import play.api.mvc._

@Singleton
class Application @Inject() extends InjectedController {

  def index = Action {
    Ok("Submodule page")
  }

}
