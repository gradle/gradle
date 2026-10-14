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
import org.gradle.integtests.fixtures.executer.ConsoleAttachment
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

import java.nio.charset.StandardCharsets

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.writeToStdInAndClose
import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

/**
 * Verifies the OSC 7501 (Program Status Protocol) reports written by the command-line client.
 *
 * The raw output is checked, because escape sequences are stripped by {@code outputContains()}.
 */
@SuppressWarnings("IntegrationTestFixtures")
@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "Program status is reported only by the console of a forked client JVM")
class ProgramStatusReportFunctionalTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            tasks.register("slow") {
                // Long enough for at least one redraw of the status bar
                doLast { Thread.sleep(500) }
            }
            tasks.register("broken") {
                dependsOn("slow")
                doLast { throw new RuntimeException("broken on purpose") }
            }
        """
    }

    def "reports working and then done for a successful build with #console console"() {
        given:
        executer.withTestConsoleAttached().withConsole(console)

        when:
        succeeds("slow")

        then:
        def reports = programStatusReports(result.output)
        reports.first().startsWith("state=working:app=gradle")
        reports.last() ==~ /state=done:app=gradle:msg=BUILD SUCCESSFUL in \d.*/
        reports.count { it.startsWith("state=done") } == 1
        !reports.any { it.startsWith("state=error") || it.startsWith("state=idle") || it.startsWith("state=blocked") }

        where:
        console << [ConsoleOutput.Rich, ConsoleOutput.Auto, ConsoleOutput.Verbose]
    }

    def "reports working and then error for a failed build"() {
        given:
        executer.withTestConsoleAttached().withConsole(ConsoleOutput.Rich)

        when:
        fails("broken")

        then:
        def reports = programStatusReports(failure.output)
        reports.first().startsWith("state=working:app=gradle")
        reports.last() ==~ /state=error:app=gradle:msg=BUILD FAILED in \d.*/
        !reports.any { it.startsWith("state=done") || it.startsWith("state=idle") }
    }

    def "reports blocked while a prompt waits for an answer"() {
        given:
        buildFile << """
            def answer = services.get(org.gradle.api.internal.tasks.userinput.UserInputHandler).askUser { it.askYesNoQuestion("Proceed?") }
            tasks.register("ask") {
                doLast { println "answer = " + answer.getOrElse("<none>") }
            }
        """
        executer.withTestConsoleAttached().withConsole(ConsoleOutput.Rich).withStdinPipe().withForceInteractiveSession(true)

        when:
        def handle = executer.withTasks("ask").start()
        poll(60) {
            assert programStatusReports(handle.standardOutput).any { it.startsWith("state=blocked") }
        }
        writeToStdInAndClose(handle, YES)
        result = handle.waitForFinish()

        then:
        outputContains("answer = true")
        def reports = programStatusReports(result.output)
        def blocked = reports.indexOf("state=blocked:kind=question:app=gradle:msg=Proceed? [yes, no]")
        blocked > 0
        reports[blocked - 1].startsWith("state=working:app=gradle")
        reports[blocked + 1].startsWith("state=working:app=gradle")
        reports.last() ==~ /state=done:app=gradle:msg=BUILD SUCCESSFUL in \d.*/
    }

    def "does not report program status with #console console and #attachment.description"() {
        given:
        executer.withTestConsoleAttached(attachment).withConsole(console)

        when:
        succeeds("slow")

        then:
        programStatusReports(result.output).empty

        where:
        attachment                             | console
        ConsoleAttachment.ATTACHED             | ConsoleOutput.Plain
        ConsoleAttachment.ATTACHED             | ConsoleOutput.Colored
        ConsoleAttachment.NOT_ATTACHED         | ConsoleOutput.Rich
        ConsoleAttachment.NOT_ATTACHED         | ConsoleOutput.Auto
        ConsoleAttachment.ATTACHED_STDERR_ONLY | ConsoleOutput.Auto
    }

    def "does not report program status when disabled with a client system property"() {
        given:
        executer.withTestConsoleAttached().withConsole(ConsoleOutput.Rich)
        executer.withCommandLineGradleOpts("-Dorg.gradle.internal.console.program-status=false")

        when:
        succeeds("slow")

        then:
        programStatusReports(result.output).empty
    }

    /**
     * Returns the bodies of the reports, with the {@code msg} value decoded.
     */
    private static List<String> programStatusReports(String output) {
        return (output =~ '\u001B\\]7501;([^\u001B]*)\u001B\\\\').collect {
            (it[1] as String).replaceAll(/msg=([A-Za-z0-9+\/=]*)/) { all, encoded ->
                "msg=" + new String(Base64.decoder.decode(encoded as String), StandardCharsets.UTF_8)
            }
        }
    }
}
