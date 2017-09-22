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

import org.gradle.api.internal.tasks.userinput.DefaultInputRequest
import org.gradle.api.internal.tasks.userinput.DefaultUserInputHandler
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class DefaultUserInputHandlerIntegrationTest extends AbstractIntegrationSpec {

    private static final String USER_INPUT_REQUEST_TASK_NAME = 'userInputRequest'
    private static final String PROMPT = 'Enter your response'
    private static final String HELLO_WORLD_USER_INPUT = 'Hello World'

    @Unroll
    def "can capture user input for interactive build [daemon enabled: #useDaemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        buildFile << userInputRequestedTask()

        when:
        executer.withTasks(USER_INPUT_REQUEST_TASK_NAME)
        withDaemon(useDaemon)
        withRichConsole(richConsole)
        def gradleHandle = executer.start()

        then:
        gradleHandle.stdinPipe.write(HELLO_WORLD_USER_INPUT.bytes)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)

        where:
        [useDaemon, richConsole] << [[false, true], [false, true]].combinations()
    }

    @Unroll
    def "use of ctrl-d when capturing user input returns null [daemon enabled: #useDaemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        buildFile << userInputRequestedTask(PROMPT, null)

        when:
        executer.withTasks(USER_INPUT_REQUEST_TASK_NAME)
        withDaemon(useDaemon)
        withRichConsole(richConsole)
        def gradleHandle = executer.start()

        then:
        gradleHandle.stdinPipe.write(4)
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)

        where:
        [useDaemon, richConsole] << [[false, true], [false, true]].combinations()
    }

    @Ignore
    @ToBeImplemented
    def "fails gracefully if console is not interactive"() {
        given:
        buildFile << userInputRequestedTask()

        when:
        def gradleHandle = executer.withTasks(USER_INPUT_REQUEST_TASK_NAME).start()

        then:
        def failure = gradleHandle.waitForFailure()
        failure.assertHasCause('Console does not support capturing input')
    }

    private void interactiveExecution() {
        executer.withStdinPipe().withForceInteractive(true)
    }

    private void withDaemon(boolean enabled) {
        if (enabled) {
            executer.requireDaemon().requireIsolatedDaemons()
        }
    }

    private void withRichConsole(boolean enabled) {
        if (enabled) {
            executer.withRichConsole()
        }
    }

    static String userInputRequestedTask(String prompt = PROMPT, String expectedInput = HELLO_WORLD_USER_INPUT) {
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
            def inputRequest = new ${DefaultInputRequest.class.getName()}('$prompt:')
            def response = userInputHandler.getInput(inputRequest)
            assert response == ${formatExpectedInput(expectedInput)}
        """
    }

    static String createUserInputHandler() {
        """
            def userInputHandler = project.services.get(${DefaultUserInputHandler.class.getName()})
        """
    }

    static String formatExpectedInput(String input) {
        if (input == null) {
            return 'null'
        }

        return "'$input'"
    }
}
