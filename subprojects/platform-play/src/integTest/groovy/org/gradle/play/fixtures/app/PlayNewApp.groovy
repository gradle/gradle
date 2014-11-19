/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.play.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class PlayNewApp extends PlayApp {

    List<SourceFile> getAppSources() {
        return [
                sourceFile("app/controllers", "Application.scala", """package controllers

                    import play.api._
                    import play.api.mvc._

                    object Application extends Controller {

                      def index = Action {
                        Ok(views.html.index("Your new application is ready."))
                      }

                    }"""),



                sourceFile("conf", "application.conf", """
                    application.secret="TY9[b`xw2MeXUt;M<i_B0kUKm8/?PD1cS1WhFYyZ[1^6`Apew34q6DyNL=UqG/1l"
                    application.langs="en"

                    # Root logger:
                    logger.root=ERROR

                    # Logger used by the framework:
                    logger.play=INFO

                    # Logger provided to your application:
                    logger.application=DEBUG"""),

                sourceFile("conf", "routes", """# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                           controllers.Application.index

# Map static resources from the /public folder to the /assets URL path
GET     /assets/*file               controllers.Assets.at(path="/public", file)

"""),

                sourceFile("public/images", "favicon.svg", """
<?xml version="1.0" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
 "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
<svg version="1.0" xmlns="http://www.w3.org/2000/svg"
 width="16.000000pt" height="16.000000pt" viewBox="0 0 16.000000 16.000000"
 preserveAspectRatio="xMidYMid meet">
<g transform="translate(0.000000,16.000000) scale(0.100000,-0.100000)"
fill="#000000" stroke="none">
</g>
</svg>"""),

                sourceFile("public/javascripts", "hello.js", """
                    if (window.console) {
                        console.log("Welcome to your Play application's JavaScript!");
                    }"""),

                sourceFile("public/stylesheets", "main.css", "")
        ]
    }

    @Override
    List<SourceFile> getViewSources() {
        return [
                sourceFile("app/views", "index.scala.html", """
                    @(message: String)

                    @main("Welcome to Play") {

                    @play20.welcome(message)

                    }"""),

                sourceFile("app/views", "main.scala.html", """
                    @(title: String)(content: Html)

                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>@title</title>
                        <link rel="stylesheet" media="screen" href="@routes.Assets.at("stylesheets/main.css")">
                        <link rel="shortcut icon" type="image/png" href="@routes.Assets.at("images/favicon.png")">
                        <script src="@routes.Assets.at("javascripts/hello.js")" type="text/javascript"></script>
                        </head>
                        <body>
                            @content
                        </body>
                    </html>""")
        ]
    }

    List<SourceFile> getTestSources() {
        return [
                sourceFile("test/javascripts", "ApplicationSpec.scala", """
                    import org.specs2.mutable._
                    import org.specs2.runner._
                    import org.junit.runner._

                    import play.api.test._
                    import play.api.test.Helpers._

                    @RunWith(classOf[JUnitRunner])
                    class ApplicationSpec extends Specification {

                      "Application" should {

                        "send 404 on a bad request" in new WithApplication{
                          route(FakeRequest(GET, "/boum")) must beNone
                        }

                        "render the index page" in new WithApplication{
                          val home = route(FakeRequest(GET, "/")).get

                          status(home) must equalTo(OK)
                          contentType(home) must beSome.which(_ == "text/html")
                          contentAsString(home) must contain ("Your new application is ready.")
                        }
                      }
                    }
                    """),

                sourceFile("test/javascripts", "IntegrationSpec.scala", """
                    import org.specs2.mutable._
                    import org.specs2.runner._
                    import org.junit.runner._

                    import play.api.test._
                    import play.api.test.Helpers._

                    /**
                     * add your integration spec here.
                     * An integration test will fire up a whole play application in a real (or headless) browser
                     */
                    @RunWith(classOf[JUnitRunner])
                    class IntegrationSpec extends Specification {

                      "Application" should {

                        "work from within a browser" in new WithBrowser {

                          browser.goTo("http://localhost:" + port)

                          browser.pageSource must contain("Your new application is ready.")
                        }
                      }
                    }
                    """)
        ]
    }
}
