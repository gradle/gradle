package controllers

import play.api._
import play.api.mvc._

import org.test.Util

object Application extends Controller {

  def index = Action {
    Ok(Util.fullStop("Your new application is ready"))
  }

  def shutdown = Action {
    System.exit(0)
    Ok("shutdown")
  }
}