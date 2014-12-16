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

package org.gradle.play.integtest

import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp

import static org.gradle.integtests.fixtures.UrlValidator.*

class AdvancedPlayApplicationIntegrationTest extends AbstractPlayAppIntegrationTest {
    PlayApp playApp = new AdvancedPlayApp()

    @Override
    def getPluginsBlock() {
        return super.getPluginsBlock() + """
            plugins {
                id 'play-coffeescript'
            }
        """
    }

    def setup() {
        buildFile << """
            repositories {
                maven {
                    name = "gradle-js"
                    url = "https://repo.gradle.org/gradle/javascript-public"
                }
            }
        """
    }

    @Override
    void verifyJar() {
        super.verifyJar()

        jar("build/playBinary/lib/play.jar").containsDescendants(
                "views/html/awesome/index.class",
                "special/strangename/Application.class",
                "models/DataType.class",
                "models/ScalaClass.class",
                "controllers/scala/MixedJava.class",
                "controllers/jva/PureJava.class",
                "public/javascripts/sample.js",
                "public/javascripts/test.js",
        )
    }

    @Override
    void verifyContent() {
        super.verifyContent()

        // Custom Routes
        assert playUrl().text.contains("<li>foo:1</li>")
        assert playUrl("root").text.contains("<li>bar:2</li>")
        assert playUrl("java/one").text.contains("Your new application is ready.")
        assert playUrl("scala/one").text.contains("<li>foo:1</li>")

        // Custom Assets
        assertUrlContent playUrl("assets/javascripts/test.js"), file("app/assets/javascripts/sample.js")
        assertUrlContent playUrl("assets/javascripts/sample.js"), file("app/assets/javascripts/sample.js")
    }
}