package controllers

import javax.inject._
import play.api._
import play.api.mvc._

@Singleton
class Application @Inject() extends InjectedController {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def square = Action {
    Ok(views.html.square("Square it!"))
  }

}
