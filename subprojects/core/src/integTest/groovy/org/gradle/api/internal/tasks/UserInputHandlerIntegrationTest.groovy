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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class UserInputHandlerIntegrationTest extends AbstractIntegrationSpec {

    public static final String USER_INPUT_SUPPORT_TASK_NAME = 'userInputSupport'
    public static final String USER_INPUT_REQUEST_TASK_NAME = 'userInputRequest'
    public static final String USER_INPUT = 'Hello World'

    @Unroll
    def "can capture user input for interactive build [daemon enabled: #useDaemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        buildFile << userInputSupportedTask(true)
        buildFile << userInputRequestedTask()

        when:
        executer.withTasks(USER_INPUT_SUPPORT_TASK_NAME, USER_INPUT_REQUEST_TASK_NAME)

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

    def "fails gracefully if console is not interactive"() {
        given:
        buildFile << userInputSupportedTask(false)
        buildFile << userInputRequestedTask()

        when:
        def gradleHandle = executer.withTasks(USER_INPUT_SUPPORT_TASK_NAME, USER_INPUT_REQUEST_TASK_NAME).start()

        then:
        def failure = gradleHandle.waitForFailure()
        failure.assertHasCause('Console does not support capturing input')
    }

    private void interactiveExecution() {
        executer.withStdinPipe().withForceInteractive(true)
    }

    static String userInputSupportedTask(boolean supported) {
        """
            import org.gradle.api.internal.tasks.UserInputHandler

            task $USER_INPUT_SUPPORT_TASK_NAME {
                doLast {
                    def userInputHandler = project.services.get(UserInputHandler)
                    assert userInputHandler.userInputSupported == $supported
                }
            }
        """
    }

    static String userInputRequestedTask() {
        """
            import org.gradle.api.internal.tasks.UserInputHandler

            task $USER_INPUT_REQUEST_TASK_NAME {
                doLast {
                    def userInputHandler = project.services.get(UserInputHandler)
                    def response = userInputHandler.getUserResponse('Enter your response:')
                    assert response == '$USER_INPUT'
                }
            }
        """
    }
}
