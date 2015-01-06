package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models.Person

object QuestionsThreeController extends Controller {
    val personForm = Form(
        mapping(
            "name" -> nonEmptyText,
            "quest" -> nonEmptyText,
            "favoriteColor" -> text
        )(Person.apply)(Person.unapply)
    )

    def submit = Action { implicit request =>
        personForm.bindFromRequest.fold(
            formWithErrors => {
                // binding failure, you retrieve the form containing errors:
                BadRequest(views.html.person(formWithErrors))
            },
            person => {
                Ok(views.html.pass())
            }
        )
    }

    def index = Action {
        Ok(views.html.person(personForm))
    }
}

