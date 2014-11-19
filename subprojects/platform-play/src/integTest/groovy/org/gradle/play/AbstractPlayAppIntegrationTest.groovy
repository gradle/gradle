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

package org.gradle.play
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.play.fixtures.app.PlayApp
import org.gradle.test.fixtures.archive.JarTestFixture

abstract class AbstractPlayAppIntegrationTest extends AbstractIntegrationSpec{
    abstract PlayApp getPlayApp()

    def setup(){
        playApp.writeSources(testDirectory.file("."))

        buildFile <<"""
        plugins {
            id 'play-application'
        }

        repositories{
            jcenter()
            maven{
                name = "typesafe-maven-release"
                url = "http://repo.typesafe.com/typesafe/maven-releases"
            }
        }
"""
    }


    def "can build play app jar"() {
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":createPlayBinaryJar", ":playBinary", ":assemble")

        and:
        jar("build/jars/play/playBinary.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "images/favicon.svg",
                "stylesheets/main.css",
                "javascripts/hello.js",
                "application.conf")

        when:
        succeeds("createPlayBinaryJar")
        then:
        skipped(":createPlayBinaryJar", ":twirlCompilePlayBinary")
    }

    def "can run play app tests"() {
        when:
        succeeds("testPlayBinary")
        then:
        executed(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/reports/test/playBinary"))


        when:
        succeeds("testPlayBinary")
        then:
        skipped(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")
    }

    void verifyTestOutput(TestExecutionResult result) {

    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
