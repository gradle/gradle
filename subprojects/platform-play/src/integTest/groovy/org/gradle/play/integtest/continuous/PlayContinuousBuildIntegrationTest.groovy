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
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.PlayApp

class PlayContinuousBuildIntegrationTest extends AbstractMultiVersionPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new BasicPlayApp(oldVersion: isOldVersion())

    def "build does not block when running play app with continuous build" () {
        when: "the build runs until it enters continuous build"
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
    }

    def "can run play app multiple times with continuous build" () {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("conf/routes") << "\n# changed"

        then:
        succeeds()

        when:
        file("conf/routes") << "\n# changed again"

        then:
        succeeds()

        when:
        file("conf/routes") << "\n# changed yet again"

        then:
        succeeds()
    }

    def "build failure prior to launch does not prevent launch on subsequent build" () {
        executer.withStackTraceChecksDisabled()
        def original = file("app/controllers/Application.scala").text

        when: "source file is broken"
        file("app/controllers/Application.scala").text = "class Application extends Controller {"

        then:
        fails("runPlayBinary")

        when: "source file is fixed"
        file("app/controllers/Application.scala").text = original

        then:
        succeeds()

        and:
        appIsRunningAndDeployed()
    }

    def "play application is stopped when build is cancelled" () {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        gradle.cancel()

        then:
        cancelsAndExits()
    }
}
