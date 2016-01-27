package controllers

import play.api._
import play.api.mvc._

import org.apache.commons.lang.StringUtils

class Application extends Controller {

  def index = Action {
    Ok(views.html.index(StringUtils.trim("   Your new application is ready.   ")))
  }

}
