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

import org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest

abstract class PlayBinaryApplicationIntegrationTest extends PlayMultiVersionRunApplicationIntegrationTest {

    def "can build play app binary"() {
        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped buildTasks

        and:
        verifyJars()

        when:
        succeeds("createPlayBinaryJar")

        then:
        skipped(":createPlayBinaryJar", ":compilePlayBinaryPlayTwirlTemplates")
    }

    def "can run play app"() {
        setup:
        patchForPlay()
        run "assemble"
        buildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = 0
                    ${java9AddJavaSqlModuleArgs()}
                }
            }
        """

        when:
        startBuild "runPlayBinary"

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        when: "stopping gradle"
        build.cancelWithEOT().waitForFinish()

        then: "play server is stopped too"
        runningApp.verifyStopped()
    }

    void verifyJars() {
        jar("build/playBinary/lib/${playApp.name}.jar").containsDescendants(
            determineRoutesClassName(),
            "views/html/index.class",
            "views/html/main.class",
            "controllers/Application.class",
            "application.conf",
            "logback.xml")
        jar("build/playBinary/lib/${playApp.name}-assets.jar").containsDescendants(
            "public/images/favicon.svg",
            "public/stylesheets/main.css",
            "public/javascripts/hello.js")
    }

    String[] getBuildTasks() {
        return [
            ":compilePlayBinaryPlayRoutes",
            ":compilePlayBinaryPlayTwirlTemplates",
            ":compilePlayBinaryScala",
            ":createPlayBinaryJar",
            ":createPlayBinaryAssetsJar",
            ":playBinary",
            ":assemble"
        ]
    }
}
