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

package org.gradle.api.internal.tasks.userinput

import spock.lang.Unroll

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.*

class DefaultBuildScanUserInputHandlerIntegrationTest extends AbstractUserInputHandlerIntegrationTest {

    private static final List<Boolean> VALID_BOOLEAN_CHOICES = [false, true]

    def setup() {
        file('buildSrc/src/main/java/BuildScanPlugin.java') << buildScanPlugin()
        buildFile << buildScanPluginApplication()
    }

    @Unroll
    def "can ask for license acceptance in interactive build [daemon enabled: #daemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withDaemon(daemon)
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdInAndClose(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)
        gradleHandle.standardOutput.contains(answerOutput(true))

        where:
        [daemon, richConsole] << [VALID_BOOLEAN_CHOICES, VALID_BOOLEAN_CHOICES].combinations()
    }

    @Unroll
    def "use of ctrl-d when asking for license acceptance returns null [daemon enabled: #daemon, rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withDaemon(daemon)
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)
        gradleHandle.standardOutput.contains(answerOutput(null))

        where:
        [daemon, richConsole] << [VALID_BOOLEAN_CHOICES, VALID_BOOLEAN_CHOICES].combinations()
    }

    @Unroll
    def "can ask for license acceptance and handle valid input '#input'"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdInAndClose(gradleHandle, stdin)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)
        gradleHandle.standardOutput.contains(answerOutput(accepted))

        where:
        input    | stdin     | accepted
        YES      | YES.bytes | true
        NO       | NO.bytes  | false
        'ctrl-d' | EOF       | null
    }

    def "can ask for license acceptance when build is executed in parallel"() {
        given:
        interactiveExecution()
        withParallel()

        buildFile << """
            subprojects {
                task $DUMMY_TASK_NAME
            }
        """
        settingsFile << "include 'a', 'b', 'c'"

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdInAndClose(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(PROMPT)
        gradleHandle.standardOutput.contains(answerOutput(true))
    }

    def "does not request user input prompt for non-interactive console"() {
        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        gradleHandle.waitForFinish()
        !gradleHandle.standardOutput.contains(PROMPT)
        gradleHandle.standardOutput.contains(answerOutput(null))
    }
}
