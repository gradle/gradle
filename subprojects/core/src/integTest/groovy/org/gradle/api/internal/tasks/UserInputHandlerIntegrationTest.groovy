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

import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class UserInputHandlerIntegrationTest extends AbstractIntegrationSpec {

    private static final String USER_INPUT_SUPPORT_TASK_NAME = 'userInputSupport'
    private static final String USER_INPUT_REQUEST_TASK_NAME = 'userInputRequest'
    private static final String HELLO_WORLD_USER_INPUT = 'Hello World'
    private static final int WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS = 20

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
        gradleHandle.stdinPipe.write(HELLO_WORLD_USER_INPUT.bytes)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.waitForFinish()

        where:
        [useDaemon, richConsole] << [[false, true], [false, true]].combinations()
    }

    def "can request user input multiple times"() {
        given:
        def ageInput = '15'
        interactiveExecution()
        buildFile << userInputRequestedTask()
        buildFile << """
            ${USER_INPUT_REQUEST_TASK_NAME}.doLast {
                ${verifyUserInput('Please provide your age', ageInput)}
            }
        """

        when:
        def gradleHandle = executer.withTasks(USER_INPUT_REQUEST_TASK_NAME).start()

        then:
        gradleHandle.stdinPipe.write(HELLO_WORLD_USER_INPUT.bytes)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.stdinPipe.write(ageInput.bytes)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.waitForFinish()
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

    @Ignore
    @ToBeImplemented
    def "can cancel build during user input"() {
        given:
        interactiveExecution()
        buildFile << userInputRequestedTask()

        when:
        def gradleHandle = executer.withTasks(USER_INPUT_REQUEST_TASK_NAME).start()

        then:
        gradleHandle.cancelWithEOT().waitForFinish()
        waitForNotRunning(gradleHandle)
        assert gradleHandle.standardOutput.contains('Build cancelled.')
    }

    private void interactiveExecution() {
        executer.withStdinPipe().withForceInteractive(true)
    }

    private waitForNotRunning(GradleHandle gradleHandle) {
        ConcurrentTestUtil.poll(WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS) {
            assert !gradleHandle.running
        }
    }

    static String userInputSupportedTask(boolean supported) {
        """
            task $USER_INPUT_SUPPORT_TASK_NAME {
                doLast {
                    ${createUserInputHandler()}
                    assert userInputHandler.userInputSupported == $supported
                }
            }
        """
    }

    static String userInputRequestedTask(String prompt = 'Enter your response', String expectedInput = HELLO_WORLD_USER_INPUT) {
        """
            task $USER_INPUT_REQUEST_TASK_NAME {
                doLast {
                    ${verifyUserInput(prompt, expectedInput)}
                }
            }
        """
    }

    static String verifyUserInput(String prompt, String expectedInput) {
        """
            ${createUserInputHandler()}
            def response = userInputHandler.getUserResponse('$prompt:')
            assert response == '$expectedInput'
        """
    }

    static String createUserInputHandler() {
        """
            def userInputHandler = project.services.get(${UserInputHandler.class.getName()})
        """
    }
}
