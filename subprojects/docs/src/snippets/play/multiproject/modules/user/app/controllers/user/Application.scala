package controllers.user

import javax.inject._
import play.api._
import play.api.mvc._

import org.sample.util.BuiltBy

@Singleton
class Application @Inject() extends InjectedController {
  def index = Action {
    Ok(views.html.user.index(BuiltBy.watermark("Here is the USER module.")))
  }
}
