package controllers.submodule

import play.api._
import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok("Submodule page")
  }

}