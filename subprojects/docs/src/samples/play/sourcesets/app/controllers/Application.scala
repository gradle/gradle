package controllers

import javax.inject._
import play.api._
import play.api.mvc._

import org.apache.commons.lang.StringUtils

@Singleton
class Application @Inject() extends InjectedController {

  def index = Action {
    Ok("   Your new application is ready.   ")
  }

}
