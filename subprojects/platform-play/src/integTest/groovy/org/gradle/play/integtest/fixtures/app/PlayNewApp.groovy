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

package org.gradle.play.integtest.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class PlayNewApp extends BasicPlayApp {

    List<SourceFile> getAppSources() {
        return [
                sourceFile("app/controllers", "Application.scala", """package controllers

                    import play.api._
                    import play.api.mvc._

                    object Application extends Controller {

                      def index = Action {
                        Ok(views.html.index("Your new application is ready."))
                      }

                    }""")

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
                sourceFile("test", "ApplicationSpec.scala", """
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

                sourceFile("test", "IntegrationSpec.scala", """
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

    @Override
    List<SourceFile> getConfSources() {
        return super.getConfSources() + [
                sourceFile("conf", "routes", """# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                           controllers.Application.index

# Map static resources from the /public folder to the /assets URL path
GET     /assets/*file               controllers.Assets.at(path="/public", file)

""")
        ]
    }
}
