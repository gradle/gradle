/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.initialization

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39539")
class BuildListenerConsoleIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "shows the #listener listeners of the #build on a worker line"() {
        given:
        server.start()
        settingsFile << "includeBuild 'lib'\n"
        file("lib/settings.gradle") << "rootProject.name = 'lib'\n"
        file("lib/build.gradle") << "tasks.register('work')\n"
        buildFile << """
            tasks.register('work') {
                dependsOn gradle.includedBuild('lib').task(':work')
            }
        """
        file(script) << """
            $registration { ${server.callFromBuild("listener")} }
        """
        def listenerCall = server.expectAndBlock("listener")

        when:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        def running = executer.withTasks('work').start()
        listenerCall.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(running, line)
        }

        cleanup:
        listenerCall?.releaseAll()
        running?.waitForFinish()

        where:
        listener             | build            | script                | registration                 | line
        "projects loaded"    | "root build"     | "settings.gradle"     | "gradle.projectsLoaded"      | "> Running projects loaded listeners"
        "projects loaded"    | "included build" | "lib/settings.gradle" | "gradle.projectsLoaded"      | "> :lib > Running projects loaded listeners"
        "projects evaluated" | "root build"     | "build.gradle"        | "gradle.projectsEvaluated"   | "> Running projects evaluated listeners"
        "projects evaluated" | "included build" | "lib/build.gradle"    | "gradle.projectsEvaluated"   | "> :lib > Running projects evaluated listeners"
        "task graph ready"   | "root build"     | "build.gradle"        | "gradle.taskGraph.whenReady" | "> Running task graph ready listeners"
        "task graph ready"   | "included build" | "lib/build.gradle"    | "gradle.taskGraph.whenReady" | "> :lib > Running task graph ready listeners"
    }
}
