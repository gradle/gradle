/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.Flaky
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.test.precondition.Requires
import org.junit.Rule
import spock.lang.Issue

/**
 * Verifies that the OSC 9;4;0 taskbar progress reset sequence is emitted when a build ends,
 * whether by normal completion or cancellation (Ctrl+C / SIGINT).
 *
 * @see <a href="https://github.com/gradle/gradle/issues/37022">Issue #37022</a>
 */
@Issue("https://github.com/gradle/gradle/issues/37022")
@Flaky(because = "https://github.com/gradle/gradle-private/issues/5132")
class TaskbarProgressResetFunctionalTest extends AbstractIntegrationSpec {
    /** OSC 9;4 prefix — signals that taskbar progress is being reported. */
    static final String OSC_PROGRESS_PREFIX = "\u001B]9;4;"

    /** OSC 9;4;0 BEL — the "remove taskbar progress" sequence. */
    static final String OSC_RESET = OSC_PROGRESS_PREFIX + "0\u0007"
    public static final int SIGINT = 2

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        // ConEmuPID triggers supportsTaskbarProgress() == true in the client JVM.
        // ConsoleOutput.Rich enables the progress bar so OSC 9;4 sequences are emitted.
        executer
            .withEnvironmentVars(ConEmuPID: "dummy")
            .withConsole(ConsoleOutput.Rich)
    }

    @Requires(value = [UnitTestPreconditions.Unix, IntegTestPreconditions.NotEmbeddedExecutor],
        reason = "sends SIGINT to a forked process works only on Unix and with a separate process")
    def "sends OSC 9;4;0 reset sequence when build receives SIGINT"() {
        given:
        // Long sleep ensures the task is still running when SIGINT is sent.
        buildFile << """
            task block {
                doFirst {
                    Thread.sleep(10_000)
                }
            }
        """

        when:
        def gradle = executer.withTasks("block").start()

        // Wait until the progress bar has started emitting OSC 9;4 sequences.
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput.contains(OSC_PROGRESS_PREFIX)
        }

        gradle.sendSignal(SIGINT)
        gradle.waitForFailure()

        then:
        gradle.standardOutput.contains(OSC_RESET)
    }

    def "sends OSC 9;4;0 reset sequence after a successful build"() {
        given:
        buildFile << """
            task block {
                doFirst {
                    ${server.callFromBuild("block")}
                }
            }
        """
        def block = server.expectAndBlock("block")

        when:
        def gradle = executer.withTasks("block").start()

        block.waitForAllPendingCalls()
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput.contains(OSC_PROGRESS_PREFIX)
        }

        block.releaseAll()
        gradle.waitForFinish()

        then:
        gradle.standardOutput.contains(OSC_RESET)
    }
}
