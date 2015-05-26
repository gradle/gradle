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
import org.gradle.play.integtest.fixtures.MultiProjectRunningPlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import spock.lang.Ignore


class PlayMultiProjectContinuousBuildIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    PlayApp playApp = new PlayMultiProject()
    RunningPlayApp runningApp = new MultiProjectRunningPlayApp(testDirectory)

    @Ignore
    def "can run multiproject play app with continuous build" () {
        when:
        // runs until continuous build mode starts
        succeeds(":primary:runPlayBinary")

        then:
        runningApp.verifyStarted()

        and:
        runningApp.verifyContent()

        cleanup: "stopping gradle"
        stopGradle()
        runningApp.verifyStopped()
    }
}
