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

import spock.lang.Specification

import static org.gradle.internal.logging.console.ProgramStatusReporter.State.BLOCKED
import static org.gradle.internal.logging.console.ProgramStatusReporter.State.DONE
import static org.gradle.internal.logging.console.ProgramStatusReporter.State.ERROR
import static org.gradle.internal.logging.console.ProgramStatusReporter.State.IDLE
import static org.gradle.internal.logging.console.ProgramStatusReporter.State.WORKING

class ProgramStatusReporterTest extends Specification {
    def output = new ByteArrayOutputStream()
    def reporter = new ProgramStatusReporter(output, 0)

    def "formats #state report with progress #progress and message '#message'"() {
        expect:
        ProgramStatusReporter.buildSequence(state, progress, message) == expected

        where:
        state   | progress | message                   | expected
        WORKING | null     | null                      | "\u001B]7501;state=working:app=gradle\u001B\\"
        WORKING | 0        | null                      | "\u001B]7501;state=working:app=gradle:progress=0\u001B\\"
        WORKING | 42       | null                      | "\u001B]7501;state=working:app=gradle:progress=42\u001B\\"
        WORKING | 100      | null                      | "\u001B]7501;state=working:app=gradle:progress=100\u001B\\"
        WORKING | 250      | null                      | "\u001B]7501;state=working:app=gradle:progress=100\u001B\\"
        WORKING | -1       | null                      | "\u001B]7501;state=working:app=gradle:progress=0\u001B\\"
        DONE    | 42       | null                      | "\u001B]7501;state=done:app=gradle\u001B\\"
        DONE    | null     | "BUILD SUCCESSFUL in 29s" | "\u001B]7501;state=done:app=gradle:msg=QlVJTEQgU1VDQ0VTU0ZVTCBpbiAyOXM=\u001B\\"
        ERROR   | null     | "BUILD FAILED in 1m 3s"   | "\u001B]7501;state=error:app=gradle:msg=QlVJTEQgRkFJTEVEIGluIDFtIDNz\u001B\\"
        ERROR   | null     | ""                        | "\u001B]7501;state=error:app=gradle\u001B\\"
        BLOCKED | null     | "Continue?"               | "\u001B]7501;state=blocked:kind=question:app=gradle:msg=Q29udGludWU/\u001B\\"
        IDLE    | null     | null                      | "\u001B]7501;state=idle:app=gradle\u001B\\"
    }

    def "turns '#text' into a single line message"() {
        expect:
        ProgramStatusReporter.toMessage(text) == expected

        where:
        text                                           | expected
        "Do you accept these terms? [yes, no] "        | "Do you accept these terms? [yes, no]"
        "Select type:\n  1: a\r\n  2: b\nEnter (1): "  | "Select type: 1: a 2: b Enter (1):"
        "\u001B[31mred\u001B[0m\t\u0007bell"          | "[31mred [0m bell"
        "naïve – ünïcødé ✓"                            | "naïve – ünïcødé ✓"
    }

    def "truncates a message to the limit of the protocol without splitting characters"() {
        expect:
        ProgramStatusReporter.toMessage("a" * 3000).length() == ProgramStatusReporter.MAX_MESSAGE_BYTES
        ProgramStatusReporter.toMessage("a" + "é" * 2000).getBytes("UTF-8").length == ProgramStatusReporter.MAX_MESSAGE_BYTES - 1
        ProgramStatusReporter.toMessage("✓" * 1000).getBytes("UTF-8").length == 2046
    }

    def "writes nothing before a state is known"() {
        when:
        reporter.flush()
        reporter.exit()

        then:
        reports() == []
    }

    def "writes a report only when state or progress changes"() {
        when:
        reporter.updateWorking(null)
        reporter.flush()
        reporter.flush()
        reporter.updateWorking(null)
        reporter.flush()
        reporter.updateWorking(10)
        reporter.flush()
        reporter.updateWorking(10)
        reporter.flush()
        reporter.updateWorking(11)
        reporter.flush()
        reporter.updateOutcome(false, "BUILD SUCCESSFUL in 1s")
        reporter.flush()
        reporter.flush()

        then:
        reports() == ["state=working:app=gradle", "state=working:app=gradle:progress=10", "state=working:app=gradle:progress=11", "state=done:app=gradle:msg=BUILD SUCCESSFUL in 1s"]
    }

    def "does not write a report until flushed"() {
        when:
        reporter.updateWorking(null)
        reporter.updateWorking(50)

        then:
        reports() == []

        when:
        reporter.flush()

        then:
        reports() == ["state=working:app=gradle:progress=50"]
    }

    def "reports blocked while a prompt waits for an answer and then the state of the build again"() {
        given:
        reporter.updateWorking(30)
        reporter.flush()

        when:
        reporter.blocked("Continue? [yes, no] ")
        reporter.updateWorking(40)
        reporter.flush()

        then:
        reports() == ["state=working:app=gradle:progress=30", "state=blocked:kind=question:app=gradle:msg=Continue? [yes, no]"]

        when:
        reporter.unblocked()

        then:
        reports() == ["state=working:app=gradle:progress=30", "state=blocked:kind=question:app=gradle:msg=Continue? [yes, no]", "state=working:app=gradle:progress=40"]
    }

    def "reports idle on exit when interrupted while #description"() {
        given:
        reporter.updateWorking(30)
        reporter.flush()
        if (prompt) {
            reporter.blocked("Continue?")
        }

        when:
        reporter.exit()
        reporter.exit()

        then:
        reports().last() == "state=idle:app=gradle"
        reports().count { it == "state=idle:app=gradle" } == 1

        where:
        description              | prompt
        "working"                | false
        "waiting for an answer"  | true
    }

    def "does not overwrite a written #expected on exit"() {
        given:
        reporter.updateWorking(30)
        reporter.flush()
        reporter.updateOutcome(failed, "BUILD")
        reporter.flush()

        when:
        reporter.exit()

        then:
        reports() == ["state=working:app=gradle:progress=30", "state=${expected}:app=gradle:msg=BUILD"]

        where:
        failed | expected
        false  | "done"
        true   | "error"
    }

    def "writes a pending #expected on exit"() {
        given:
        reporter.updateWorking(30)
        reporter.flush()
        reporter.updateOutcome(failed, "BUILD")

        when:
        reporter.exit()

        then:
        reports() == ["state=working:app=gradle:progress=30", "state=${expected}:app=gradle:msg=BUILD"]

        where:
        failed | expected
        false  | "done"
        true   | "error"
    }

    def "writes nothing on exit when working was never written"() {
        given:
        reporter.updateWorking(null)

        when:
        reporter.exit()

        then:
        reports() == []
    }

    private List<String> reports() {
        def text = output.toString("US-ASCII")
        def matcher = text =~ '\u001B\\]7501;([^\u001B]*)\u001B\\\\'
        assert matcher.replaceAll("") == "": "unexpected output: ${text}"
        return matcher.collect { decodeMessage(it[1] as String) }
    }

    static String decodeMessage(String report) {
        return report.replaceAll(/msg=([A-Za-z0-9+\/=]*)/) { all, encoded -> "msg=" + new String(Base64.decoder.decode(encoded as String), "UTF-8") }
    }
}
