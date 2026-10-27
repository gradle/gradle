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
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.test.precondition.Requires
import org.junit.Assume
import spock.lang.Issue

/**
 * Verifies that the OSC 9;4;0 taskbar progress reset sequence is emitted when a build ends,
 * whether by normal completion or cancellation (Ctrl+C / SIGINT).
 *
 * @see <a href="https://github.com/gradle/gradle/issues/37022">Issue #37022</a>
 */
@Issue("https://github.com/gradle/gradle/issues/37022")
class TaskbarProgressResetFunctionalTest extends AbstractIntegrationSpec {
    /** OSC 9;4 prefix — signals that taskbar progress is being reported. */
    static final String OSC_PROGRESS_PREFIX = "\u001B]9;4;"

    /** OSC 9;4;0 BEL — the "remove taskbar progress" sequence. */
    static final String OSC_RESET = OSC_PROGRESS_PREFIX + "0\u0007"
    public static final int SIGINT = 2

    def setup() {
        // ConEmuPID triggers supportsTaskbarProgress() == true in the client JVM.
        // ConsoleOutput.Rich enables the progress bar so OSC 9;4 sequences are emitted.
        executer
            .requireIsolatedDaemons()
            .withEnvironmentVars(ConEmuPID: "dummy")
            .withConsole(ConsoleOutput.Rich)
    }

    @Requires(value = [OsTestPreconditions.Unix, TestExecutionPreconditions.NotEmbeddedExecutor],
        reason = "sends SIGINT to a forked process works only on Unix and with a separate process")
    def "sends OSC 9;4;0 reset sequence when build receives SIGINT"() {
        given:
        // On background/service processes such as CI build agents, SIGINT is inherited
        // as SIG_IGN — a disposition that survives exec and that the JVM leaves in place
        // — so `kill -SIGINT` on the forked client is a no-op and the build can never be
        // cancelled (https://github.com/gradle/gradle-private/issues/5153). Skip rather
        // than fail where the signal is undeliverable; the test still runs wherever
        // SIGINT works (locally, interactive shells, or any agent where it is delivered).
        Assume.assumeTrue(
            "SIGINT is ignored (SIG_IGN) in this process tree, so it cannot cancel the build; see gradle-private#5153",
            sigintDeliverableToForkedChild())

        // The task creates a marker file once it's running, then sleeps.
        // We wait for the marker so the build is actually executing (cancellable) when
        // SIGINT arrives.
        def readyFile = file("ready.marker")
        buildFile << """
            task block {
                def marker = file("${readyFile.name}")
                doFirst {
                    marker.createNewFile()
                    Thread.sleep(600_000)
                }
            }
        """

        when:
        def gradle = executer.withTasks("block").start()

        // Wait until the task is actually executing (the daemon has created the
        // marker) before signalling, so the build is cancellable when SIGINT arrives.
        ConcurrentTestUtil.poll {
            assert readyFile.exists()
        }

        // A single SIGINT can be lost before the client JVM acts on it: on loaded
        // machines the build is otherwise observed to run to normal completion
        // instead of cancelling. Resend the signal until the process terminates.
        ConcurrentTestUtil.poll(60, 0, 1) {
            if (gradle.running) {
                gradle.sendSignal(SIGINT)
            }
            assert !gradle.running
        }
        gradle.waitForFailure()

        then:
        gradle.standardOutput.contains(OSC_RESET)
    }

    /**
     * Whether SIGINT can be delivered to a child process forked by this test worker
     * (and therefore to the forked gradle client). Detected by forking a child that
     * signals itself: it survives if SIGINT is ignored, and is killed if it is deliverable.
     */
    private static boolean sigintDeliverableToForkedChild() {
        try {
            def probe = new ProcessBuilder("sh", "-c", 'kill -INT $$; echo SURVIVED-SIGINT')
                .redirectErrorStream(true)
                .start()
            def out = probe.inputStream.text.trim()
            probe.waitFor()
            return !out.contains("SURVIVED-SIGINT")
        } catch (Exception e) {
            // If the probe itself fails, assume deliverable so the test still runs.
            System.err.println("Could not determine SIGINT disposition: ${e}")
            return true
        }
    }

    @SuppressWarnings("IntegrationTestFixtures") // outputContains() strips ANSI escape characters; we need raw output to verify the OSC sequence
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "OSC taskbar progress sequences are only emitted by the forked client JVM")
    def "sends OSC 9;4;0 reset sequence after a successful build"() {
        given:
        buildFile << """
            task ok { }
        """

        when:
        result = succeeds("ok")

        then:
        result.output.contains(OSC_RESET)
    }

    @Issue("https://github.com/gradle/gradle/issues/37611")
    @SuppressWarnings("IntegrationTestFixtures")
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "OSC taskbar progress sequences are only emitted by the forked client JVM")
    def "does not emit OSC 9;4 sequences when --console=plain"() {
        given:
        executer.withConsole(ConsoleOutput.Plain)
        buildFile << """
            task ok { }
        """

        when:
        result = succeeds("ok")

        then:
        !result.output.contains(OSC_PROGRESS_PREFIX)
    }

    @Issue("https://github.com/gradle/gradle/issues/37611")
    @SuppressWarnings("IntegrationTestFixtures")
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "OSC taskbar progress sequences are only emitted by the forked client JVM")
    def "does not emit OSC 9;4 sequences when console is Auto and stdout is not a terminal"() {
        given:
        executer.withConsole(ConsoleOutput.Auto)
        buildFile << """
            task ok { }
        """

        when:
        result = succeeds("ok")

        then:
        !result.output.contains(OSC_PROGRESS_PREFIX)
    }
}
