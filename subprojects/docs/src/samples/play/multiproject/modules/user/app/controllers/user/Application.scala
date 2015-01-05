package controllers.user

import play.api._
import play.api.mvc._

object Application extends Controller {
  def index = Action {
    Ok("Here is the USER module")
  }
}