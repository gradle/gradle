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

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.*

class DefaultBuildScanUserInputHandlerIntegrationTest extends AbstractUserInputHandlerIntegrationTest {

    private static final String PLAIN_CONSOLE = 'plain'
    private static final String RICH_CONSOLE = 'rich'

    def setup() {
        file('buildSrc/src/main/java/BuildScanPlugin.java') << buildScanPlugin()
        buildFile << buildScanPluginApplication()
    }

    @Unroll
    def "can ask for license acceptance in interactive build without daemon and #description console"() {
        given:
        interactiveExecution()
        withConsoleOutput(consoleOutput)

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdIn(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        closeStdIn(gradleHandle)
        expectRenderedPromptAndAnswer(gradleHandle, true)

        where:
        consoleOutput       | description
        ConsoleOutput.Plain | PLAIN_CONSOLE
        ConsoleOutput.Rich  | RICH_CONSOLE
    }

    @Ignore("flaky test - sometimes fails with java.io.IOException: Write end dead or Pipe closed")
    @Unroll
    def "can ask for license acceptance in interactive build with daemon and #description console"() {
        given:
        interactiveExecution()
        withConsoleOutput(consoleOutput)
        withDaemon()

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdIn(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        closeStdIn(gradleHandle)
        expectRenderedPromptAndAnswer(gradleHandle, true)

        cleanup:
        daemons.daemon.kill()

        where:
        consoleOutput       | description
        ConsoleOutput.Plain | PLAIN_CONSOLE
        ConsoleOutput.Rich  | RICH_CONSOLE
    }

    @Unroll
    def "use of ctrl-d when asking for license acceptance returns null without daemon and #description console"() {
        given:
        interactiveExecution()
        withConsoleOutput(consoleOutput)

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        gradleHandle.cancelWithEOT().waitForFinish()
        expectRenderedPromptAndAnswer(gradleHandle, null)

        where:
        consoleOutput       | description
        ConsoleOutput.Plain | PLAIN_CONSOLE
        ConsoleOutput.Rich  | RICH_CONSOLE
    }

    @Ignore("flaky test - sometimes fails with java.io.IOException: Write end dead or Pipe closed")
    @Unroll
    def "use of ctrl-d when asking for license acceptance returns null with daemon and #description console"() {
        given:
        interactiveExecution()
        withConsoleOutput(consoleOutput)
        withDaemon()

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        gradleHandle.cancelWithEOT().waitForFinish()
        closeStdIn(gradleHandle)
        expectRenderedPromptAndAnswer(gradleHandle, null)

        cleanup:
        daemons.daemon.kill()

        where:
        consoleOutput       | description
        ConsoleOutput.Plain | PLAIN_CONSOLE
        ConsoleOutput.Rich  | RICH_CONSOLE
    }

    @Unroll
    def "can ask for license acceptance and handle valid input '#input'"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        writeToStdIn(gradleHandle, stdin)
        gradleHandle.waitForFinish()
        closeStdIn(gradleHandle)
        expectRenderedPromptAndAnswer(gradleHandle, accepted)

        where:
        input    | stdin     | accepted
        YES      | YES.bytes | true
        NO       | NO.bytes  | false
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
        writeToStdIn(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        closeStdIn(gradleHandle)
        expectRenderedPromptAndAnswer(gradleHandle, true)
    }

    def "does not request user input prompt for non-interactive console"() {
        when:
        def gradleHandle = executer.withTasks(DUMMY_TASK_NAME).start()

        then:
        gradleHandle.waitForFinish()
        expectNoPromptAndNullAnswer(gradleHandle)
    }

    private DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
