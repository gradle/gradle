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

package org.gradle.api.internal.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class UserInputHandlerIntegrationTest extends AbstractIntegrationSpec {

    public static final String USER_INPUT = 'Hello World'

    @Unroll
    def "can capture user input for interactive build [daemon enabled: #useDaemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        buildFile << userInputTask()

        when:
        executer.withTasks('ask')

        if (useDaemon) {
            executer.requireDaemon().requireIsolatedDaemons()
        }

        if (richConsole) {
            executer.withRichConsole()
        }

        def gradleHandle = executer.start()

        then:
        gradleHandle.stdinPipe.write(USER_INPUT.bytes)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.waitForFinish()

        where:
        [useDaemon, richConsole] << [[false, true], [false, true]].combinations()
    }

    @NotYetImplemented
    def "fails gracefully if console is not interactive"() {
        given:
        buildFile << userInputTask()

        when:
        def gradleHandle = executer.withTasks('ask').start()

        then:
        def failure = gradleHandle.waitForFailure()
        failure.assertHasCause('Console does not support capturing input')
    }

    private void interactiveExecution() {
        executer.withStdinPipe().withForceInteractive(true)
    }

    static String userInputTask() {
        """
            import org.gradle.api.internal.tasks.UserInputHandler

            task ask {
                doLast {
                    def userInputHandler = project.services.get(UserInputHandler)
                    def response = userInputHandler.getUserResponse('Enter your response:')
                    assert response == '$USER_INPUT'
                }
            }
        """
    }
}
