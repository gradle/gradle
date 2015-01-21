package controllers

import play.api._
import play.api.mvc._

import org.sample.util.BuiltBy

object Application extends Controller {
  def index = Action {
    Ok(views.html.index(BuiltBy.watermark("Here is a multiproject app!")))
  }
}