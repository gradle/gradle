package controllers.admin

import play.api._
import play.api.mvc._

object Application extends Controller {
  def index = Action {
    Ok("Here is the ADMIN module")
  }
}