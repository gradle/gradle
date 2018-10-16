package controllers

import javax.inject._
import play.api._
import play.api.mvc._

import org.test.Util

@Singleton
class Application @Inject() extends InjectedController {

  def index = Action {
    Ok(Util.fullStop("Your new application is ready"))
  }

  def shutdown = Action {
    Runtime.getRuntime().halt(0)
    Ok("shutdown")
  }
}
