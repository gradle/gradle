package controllers.user

import play.api._
import play.api.mvc._

import org.sample.util.BuiltBy

object Application extends Controller {
  def index = Action {
    Ok(views.html.user.index(BuiltBy.watermark("Here is the USER module.")))
  }
}