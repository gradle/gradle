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

import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest
import org.gradle.util.TextUtil

abstract class PlayBinaryApplicationIntegrationTest extends PlayMultiVersionRunApplicationIntegrationTest {

    def "can build play app binary"() {
        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryRoutes",
                ":compilePlayBinaryTwirlTemplates",
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":assemble")

        and:
        verifyJars()

        when:
        succeeds("createPlayBinaryJar")

        then:
        skipped(":createPlayBinaryJar", ":compilePlayBinaryTwirlTemplates")
    }

    def "can run play app"() {
        setup:
        buildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = ${runningApp.selectPort()}
                }
            }
        """
        run "assemble"

        when:
        def userInput = new PipedOutputStream();
        executer.withStdIn(new PipedInputStream(userInput))
        GradleHandle gradleHandle = executer.withTasks("runPlayBinary").start()

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        when: "stopping gradle"
        userInput.write(4) // ctrl+d
        userInput.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter

        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        runningApp.verifyStopped()
    }

    void verifyJars() {
        jar("build/playBinary/lib/${playApp.name}.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "application.conf")
        jar("build/playBinary/lib/${playApp.name}-assets.jar").containsDescendants(
                "public/images/favicon.svg",
                "public/stylesheets/main.css",
                "public/javascripts/hello.js")
    }
}
