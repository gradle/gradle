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

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class BuildStatusRendererFunctionalTest extends AbstractConsoleFunctionalSpec {
    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    GradleHandle gradle

    def setup() {
        settingsFile << """
            // wait for the initialization phase
            new URL('${server.uri}').text
        """
        buildFile << """
            // wait for the configuration phase 
            new URL('${server.uri}').text
            task hello { 
                doFirst {
                    // wait for the execution phase
                    println 'hello world' 
                    new URL('${server.uri}').text
                } 
            }
        """
    }

    def "shows progress bar and percent phase completion"() {
        given:
        gradle = executer.withTasks("hello").start()

        expect:
        server.waitFor()
        assertHasBuildPhase("INITIALIZING")
        server.release()

        and:
        server.waitFor()
        assertHasBuildPhase("CONFIGURING")
        server.release()

        and:
        server.waitFor()
        assertHasBuildPhase("EXECUTING")
        server.release()

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
