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

class CustomPlayApp extends BasicPlayApp{
    @Override
    List<SourceFile> getAppSources() {
        return [
                sourceFile("app/models", "ScalaClass.scala", """
                package models

                class ScalaClass(val name: String)
                """),

                sourceFile("app/models", "DataType.java", """
                    package models;

                    public class DataType {
                        private final String foo;
                        private final int bar;

                        public DataType(String foo, int bar) {
                            this.foo = foo;
                            this.bar = bar;
                        }
                    }
                """),

                sourceFile("app/controllers", "MixedJava.java", """
                    package controllers;

                    import play.*;
                    import play.mvc.*;
                    import views.html.*;

                    public class MixedJava extends Controller {

                        public static Result index() {
                            System.out.println(new models.ScalaClass("Java can also reference Scala files"));
                            return ok(index.render("Your new mixed application is ready."));
                        }

                    }
                """),

                sourceFile("app/controllers", "PureJava.java", """
                    package controllers;

                    import play.*;
                    import play.mvc.*;
                    import views.html.*;

                    public class PureJava extends Controller {

                        public static Result index() {
                            return ok(index.render("Your new application is ready."));
                        }

                    }
                """),

                sourceFile("app/special/strangename", "Application.scala", """

                    package special.strangename

                    import play.api._
                    import play.api.mvc._

                    object Application extends Controller {

                      def index = Action {
                        Ok(views.html.index("Your new application is ready."))
                      }

                    }
                """),



                sourceFile("app/controllers", "Application.scala", """
                    package controllers

                    import play.api._
                    import play.api.mvc._

                    import models._

                    object Application extends Controller {

                      def index = Action {
                        Ok(views.html.awesome.index(List(new DataType("foo", 1))))
                      }

                      def root = Action {
                        Ok(views.html.awesome.index(List(new DataType("bar", 2))))
                      }

                    }
                """)


        ]
    }

    @Override
    List<SourceFile> getViewSources() {
        return [
                sourceFile("app/views/awesome", "index.scala.html", """
                @(stuff: List[DataType])

                <ul>
                    @for(s <- stuff) {
                    <li>@s</li>
                    }
                </ul>
                """),

                sourceFile("app/views", "index.scala.html", """
                @(message: String)

                @main("Welcome to Play") {

                @play20.welcome(message)

                @awesome.index(List(new DataType("hello", 1)))

                }
                """),

                sourceFile("app/views", "main.scala.html", """

                @(title: String)(content: Html)
                <html>
                    <head>
                        <title>@title</title>
                        @**<link rel="stylesheet" media="screen" href="@routes.Assets.at("stylesheets/main.css")">
                        <link rel="shortcut icon" type="image/png" href="@routes.Assets.at("images/favicon.png")">
                        <script src="@routes.Assets.at("javascripts/hello.js")" type="text/javascript"></script>**@
                    </head>
                    <body>
                        @content
                    </body>
                </html>
                """)

        ]
    }

    @Override
    List<SourceFile> getTestSources() {
        return []
    }

    @Override
    List<SourceFile> getConfSources() {
        return super.getConfSources() + [
                sourceFile("conf", "routes", """# Routes
GET /          controllers.PureJava.index
->  /scala     scala.Routes
->  /java      jva.Routes
                    """),

                sourceFile("conf", "jva.routes", "GET        /one         controllers.Application.index"),

                sourceFile("conf", "scala.routes", """
GET        /one         controllers.MixedJava.index
POST       /two         special.strangename.Application.index
                    """)


        ]
    }
}
