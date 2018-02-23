package controllers.admin

import play.api._
import play.api.mvc._

import org.sample.util.BuiltBy

object Application extends Controller {
  def index = Action {
    Ok(views.html.admin.index(BuiltBy.watermark("Here is the ADMIN module.")))
  }
}