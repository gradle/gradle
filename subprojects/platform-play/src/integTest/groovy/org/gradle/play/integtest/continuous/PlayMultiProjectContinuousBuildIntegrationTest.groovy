/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest.continuous

import org.gradle.play.integtest.fixtures.AbstractMultiVersionPlayContinuousBuildIntegrationTest
import org.gradle.play.integtest.fixtures.MultiProjectRunningPlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest.*


class PlayMultiProjectContinuousBuildIntegrationTest extends AbstractMultiVersionPlayContinuousBuildIntegrationTest {
    PlayApp playApp = new PlayMultiProject()
    PlayApp childApp = new BasicPlayApp()
    TestFile childDirectory = testDirectory.file('child')
    RunningPlayApp runningApp = new MultiProjectRunningPlayApp(testDirectory)
    RunningPlayApp runningChildApp = new RunningPlayApp(childDirectory)
    TestFile playRunBuildFile = file("primary/build.gradle")

    def "can run multiproject play app with continuous build" () {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        and:
        doesntExit()

        cleanup: "stopping gradle"
        stopGradle()
        appIsStopped()
    }

    def "can run play apps in multiple projects in multiproject continuous build" () {
        includeChildApp()

        when:
        succeeds(":primary:runPlayBinary", ":child:runPlayBinary")

        then:
        executedAndNotSkipped(":primary:runPlayBinary", ":child:runPlayBinary")

        and:
        appIsRunningAndDeployed()
        childAppIsRunningAndDeployed()

        when:
        file('primary/conf/routes') << "# some change"

        then:
        succeeds()

        when:
        childDirectory.file('conf/routes') << "# some change"

        then:
        succeeds()

        when:
        sendEOT()

        then:
        cancelsAndExits()

        and:
        appIsStopped()
        childAppIsStopped()
    }

    def "show build failures in play apps in multiple projects in multiproject continuous build" () {
        includeChildApp()

        when:
        succeeds(":primary:runPlayBinary", ":child:runPlayBinary")

        then:
        appIsRunningAndDeployed()
        childAppIsRunningAndDeployed()

        when:
        addBadScala("primary/app")

        then:
        fails()
        notExecuted(":primary:runPlayBinary")
        errorPageHasTaskFailure(":primary:compilePlayBinaryScala")
        childErrorPageHasTaskFailure(":primary:compilePlayBinaryScala")

        when:
        fixBadScala("primary/app")
        then:
        succeeds()
        appIsRunningAndDeployed()
        childAppIsRunningAndDeployed()
    }

    private void includeChildApp() {
        childApp.writeSources(childDirectory)
        childDirectory.file('build.gradle') << """
            model {
                tasks.runPlayBinary {
                    httpPort = 0
                    ${java9AddJavaSqlModuleArgs()}
                }
            }

            // ensure that child run task always runs second, even with --parallel
            tasks.withType(PlayRun) {
                dependsOn project(':primary').tasks.withType(PlayRun)
            }
        """
        file('settings.gradle') << """
            include ':child'
        """
    }

    def addBadScala(path) {
        file("$path/models/NewType.scala") << """
package models

object NewType {
"""
    }

    def fixBadScala(path) {
        file("$path/models/NewType.scala") << """
}
"""
    }

    def childAppIsRunningAndDeployed() {
        runningChildApp.initialize(gradle)
        runningChildApp.verifyStarted('', 1)
        runningChildApp.verifyContent()
        true
    }

    def childAppIsStopped() {
        runningChildApp.requireHttpPort(1)
        runningChildApp.verifyStopped()
        true
    }

    private errorPageHasTaskFailure(task) {
        def error = runningApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;$task&#x27;.")
        error
    }
    private childErrorPageHasTaskFailure(task) {
        def error = runningChildApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;$task&#x27;.")
        error
    }
}
