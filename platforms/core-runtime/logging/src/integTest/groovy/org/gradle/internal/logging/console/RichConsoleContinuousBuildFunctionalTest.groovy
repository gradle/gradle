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

package org.gradle.internal.logging.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.fixtures.ConcurrentTestUtil

class RichConsoleContinuousBuildFunctionalTest extends AbstractContinuousIntegrationTest {

    def setup() {
        executer.withTestConsoleAttached()
        executer.beforeExecute {
            it.withConsole(ConsoleOutput.Rich)
        }
    }

    def "shows WAITING after every build"() {
        given:
        file("src/input.txt").text = "one"
        buildFile << """
            tasks.register('copy', Copy) {
                from 'src'
                into layout.buildDirectory.dir('out')
            }
        """

        when:
        succeeds("copy")

        then:
        assertLastBuildStatus("WAITING")

        when:
        update(file("src/input.txt"), "two")

        then:
        buildTriggeredAndSucceeded()
        assertLastBuildStatus("WAITING")
    }

    void assertLastBuildStatus(String expected) {
        ConcurrentTestUtil.poll {
            assert RenderedBuildStatus.last(gradle.standardOutput) == expected
        }
    }
}
