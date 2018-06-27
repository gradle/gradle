/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class BuildStatusRendererFunctionalTest extends AbstractIntegrationSpec implements RichConsoleStyling {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    GradleHandle gradle

    def setup() {
        executer.withConsole(ConsoleOutput.Rich)
        server.start()
        settingsFile << """
            // wait for the initialization phase
            ${server.callFromBuild('settings')}
        """
        buildFile << """
            // wait for the configuration phase 
            ${server.callFromBuild('build-script')}
            task hello { 
                doFirst {
                    // wait for the execution phase
                    println 'hello world' 
                    ${server.callFromBuild('task')}
                } 
            }
        """
    }

    def "shows progress bar and percent phase completion"() {
        given:
        def settings = server.expectAndBlock('settings')
        def buildScript = server.expectAndBlock('build-script')
        def task = server.expectAndBlock('task')
        gradle = executer.withTasks("hello").start()

        expect:
        settings.waitForAllPendingCalls()
        assertHasBuildPhase("INITIALIZING")
        settings.releaseAll()

        and:
        buildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING")
        buildScript.releaseAll()

        and:
        task.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING")
        task.releaseAll()

        cleanup:
        gradle?.waitForFinish()
    }

    private void assertHasBuildPhase(String message) {
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput =~ regexFor(message)
        }
    }

    private String regexFor(String message) {
        /<.*> \d{1,3}% $message \[\d+s]/
    }
}
