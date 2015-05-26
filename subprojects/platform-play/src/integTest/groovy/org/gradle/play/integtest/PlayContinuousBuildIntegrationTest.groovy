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

package org.gradle.play.integtest

import org.gradle.play.integtest.fixtures.AbstractPlayContinuousBuildIntegrationTest
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.util.TextUtil
import spock.lang.Ignore

class PlayContinuousBuildIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new BasicPlayApp()

    @Ignore
    def "build does not block when running play app with continuous build" () {
        when:
        // runs until continuous build mode starts
        succeeds("runPlayBinary")

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        cleanup: "stopping gradle"
        stopGradle()
        runningApp.verifyStopped()
    }

    @Ignore
    def "build failure prior to launch does not prevent launch on subsequent build" () {
        when:
        file('app/controllers/Application.scala').text = "object Application extends Controller {"

        then:
        fails("runPlayBinary")

        when:
        playApp.writeSources(testDirectory)

        then:
        succeeds("runPlayBinary")

        and:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        cleanup: "stopping gradle"
        stopGradle()
        runningApp.verifyStopped()
    }

    @Ignore
    def "play application is stopped when build is cancelled" () {
        when:
        // runs until continuous build mode starts
        succeeds("runPlayBinary")

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        when:
        println "sending ctrl-d"
        stdinPipe.write(4) // ctrl-d
        stdinPipe.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter

        then:
        stopGradle()

        and:
        runningApp.verifyStopped()
    }

    def "play run task blocks when not using continuous build" () {
        when:
        executer.withStdIn(System.in)
        gradle = executer.withTasks("runPlayBinary").withForceInteractive(true).start()

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        when:
        println "sending ctrl-d"
        stdinPipe.write(4) // ctrl-d
        stdinPipe.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter

        then:
        stopGradle()

        and:
        runningApp.verifyStopped()
    }
}
