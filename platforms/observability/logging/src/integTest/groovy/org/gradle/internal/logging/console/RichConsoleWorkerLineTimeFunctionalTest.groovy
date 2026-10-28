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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.LogContent
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.junit.Rule
import spock.lang.Issue

import java.util.regex.Pattern

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "The system property of the console is only set for a forked build")
@Issue("https://github.com/gradle/gradle/issues/39559")
class RichConsoleWorkerLineTimeFunctionalTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "shows how long a worker line has run"() {
        given:
        server.start()
        buildFile << """
            tasks.register('work') {
                doLast {
                    ${server.callFromBuild("work")}
                }
            }
        """
        def call = server.expectAndBlock("work")

        when:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        // The time is shown after 1 s, not 10 s, so the test is quicker
        executer.withCommandLineGradleOpts("-Dorg.gradle.internal.console.time-shown-after=1000")
        def running = executer.withTasks('work').start()
        call.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            def output = LogContent.of(running.standardOutput).ansiCharsToColorText().withNormalizedEol()
            assert output =~ Pattern.quote(RichConsoleStyling.workInProgressLine("> :work")) + /  \d+s/
        }

        cleanup:
        call?.releaseAll()
        running?.waitForFinish()
    }
}
