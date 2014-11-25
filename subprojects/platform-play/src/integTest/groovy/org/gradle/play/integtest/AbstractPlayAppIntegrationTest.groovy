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
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.play.integtest.fixtures.MultiPlayVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.util.AvailablePortFinder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.integtests.fixtures.UrlValidator.*

abstract class AbstractPlayAppIntegrationTest extends MultiPlayVersionIntegrationTest{

    abstract PlayApp getPlayApp()

    def portFinder = AvailablePortFinder.createPrivate()
    int httpPort = portFinder.nextAvailable

    def setup(){

        playApp.writeSources(testDirectory.file("."))
    }

    def "can build play app jar"() {
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(":processPlayBinaryPlayJavaScriptSources", ":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":createPlayBinaryJar", ":playBinary", ":assemble")

        and:
        jar("build/jars/play/playBinary.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "images/favicon.svg",
                "stylesheets/main.css",
                "javascripts/hello.js",
                "js/test.js",
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
        executed(":processPlayBinaryPlayJavaScriptSources", ":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/reports/test/playBinary"))


        when:
        succeeds("testPlayBinary")
        then:
        skipped(":processPlayBinaryPlayJavaScriptSources", ":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can run play app"(){
        setup:
        buildFile <<"""
        model {
            tasks.runPlayBinary {
                httpPort = $httpPort
            }
        }"""
        when:
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        available("http://localhost:$httpPort", "Play app", 120000)
        assert new URL("http://localhost:$httpPort").text.contains("Your new application is ready.")

        when: "stopping gradle"
        gradleHandle.abort()
        gradleHandle.waitForFailure()

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort")
    }

    void verifyTestOutput(TestExecutionResult result) {
    }


}
