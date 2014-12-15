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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.play.integtest.fixtures.MultiPlayVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.util.AvailablePortFinder
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.UrlValidator.*

abstract class AbstractPlayAppIntegrationTest extends MultiPlayVersionIntegrationTest {
    int httpPort

    abstract PlayApp getPlayApp()
    def portFinder = AvailablePortFinder.createPrivate()

    def setup(){
        playApp.writeSources(testDirectory.file("."))
    }

    def "can build play app jar"() {
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":createPlayBinaryJar", ":playBinary", ":assemble")

        and:
        verifyJar()

        when:
        succeeds("createPlayBinaryJar")
        then:
        skipped(":createPlayBinaryJar", ":twirlCompilePlayBinary")
    }

    def "can run play app tests"() {
        setup:
        int testPort = portFinder.nextAvailable
        buildFile << """
        model {
            tasks.testPlayBinary{
                systemProperty 'testserver.port', $testPort
            }
        }
        """

        when:
        succeeds("check")
        then:
        executed(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test", "xml"))

        when:
        succeeds("check")
        then:
        skipped(":routesCompilePlayBinary", ":twirlCompilePlayBinary", ":scalaCompilePlayBinary",
                ":createPlayBinaryJar", ":playBinary", ":compilePlayBinaryTests", ":testPlayBinary")
    }

    /**
     * Don't currently run with DaemonExecuter, because
     * InputForwarder is consuming stdin eagerly.
     * */
    @IgnoreIf({ GradleContextualExecuter.isDaemon() })
    def "can run play app"(){
        setup:
        httpPort = portFinder.nextAvailable

        buildFile <<
        """
        model {
            tasks.runPlayBinary {
                httpPort = $httpPort
            }
        }"""
        run "assemble"

        when:
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(inputStream);
        executer.withStdIn(inputStream)
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        def url = playUrl().toString()
        available(url, "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        verifyContent()

        when: "stopping gradle"
        stdinWriter.write(4) // ctrl+d
        stdinWriter.flush()
        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable(url)
    }

    void verifyJar() {
        jar("build/playBinary/lib/play.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "public/images/favicon.svg",
                "public/stylesheets/main.css",
                "public/javascripts/hello.js",
                "application.conf")
    }

    void verifyContent() {
        // Check all static assets from the shared content
        assertUrlContent playUrl("assets/stylesheets/main.css"), file("public/stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), file("public/javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.svg"), file("public/images/favicon.svg")
    }

    URL playUrl(String path='') {
        return new URL("http://localhost:$httpPort/${path}")
    }

    void verifyTestOutput(TestExecutionResult result) {
    }
}
