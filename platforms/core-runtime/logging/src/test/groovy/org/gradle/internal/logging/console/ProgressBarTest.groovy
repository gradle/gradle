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

import org.gradle.internal.logging.events.StyledTextOutputEvent.Span
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Specification
import spock.lang.Subject

@Subject(ProgressBar)
class ProgressBarTest extends Specification {

    private static final String BUILD_PHASE = 'EXECUTING'

    ProgressBar progressBar

    private void init(boolean unicode, int totalProgress = 0, int cols = 0, boolean taskbarProgress = false) {
        def consoleMetaData = Stub(ConsoleMetaData) {
            supportsUnicode() >> unicode
            supportsTaskbarProgress() >> taskbarProgress
            getCols() >> cols
        }
        progressBar = unicode
            ? ProgressBar.getUnicodeProgressBar(consoleMetaData, BUILD_PHASE, totalProgress)
            : ProgressBar.getAsciiProgressBar(consoleMetaData, BUILD_PHASE, totalProgress)
    }

    private List<Span> frame(long now = 0, boolean timerEnabled = false) {
        progressBar.formatProgress(timerEnabled, now)
    }

    private String line(long now = 0, boolean timerEnabled = false) {
        frame(now, timerEnabled).collect { it.text }.join("")
    }

    private static StyledTextOutput.Style styleOf(List<Span> spans, String text) {
        spans.find { it.text.contains(text) }.style
    }

    def "formats the line without work (#id)"() {
        given:
        init(unicode)

        expect:
        line(1000, true) == withDuration
        line(1000, false) == withoutDuration

        where:
        unicode | withDuration                                          | withoutDuration
        true    | "│······························│» EXECUTING  [1s]"    | "│······························│» EXECUTING"
        false   | "[..............................]> EXECUTING  [1s]"   | "[..............................]> EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "shows the running and queued counts once work is known (#id)"() {
        given:
        init(unicode, 10)
        3.times { progressBar.update(false) }
        progressBar.setRunning(2)

        expect:
        line() == expected

        where:
        unicode | expected
        true    | "│█████████▶▶▶▶▶▶···············│» EXECUTING  ▶ 2  … 5 "
        false   | "[#########======...............]> EXECUTING  2 running, 5 queued "
        id = unicode ? "unicode" : "ascii"
    }

    def "pads the counts to the widest value seen (#id)"() {
        given:
        init(unicode, 1500)
        progressBar.setRunning(10)
        frame(0)

        when:
        1000.times { progressBar.update(false) }
        progressBar.setRunning(2)

        then:
        line(100) == expected

        where:
        unicode | expected
        true    | "│█████████████████▏▶···········│» EXECUTING  ▶  2  …   498 "
        false   | "[#################=............]> EXECUTING   2 running,   498 queued "
        id = unicode ? "unicode" : "ascii"
    }

    def "scales the done, running and queued segments (#id)"() {
        given:
        init(unicode, 6560)
        1240.times { progressBar.update(false) }
        progressBar.setRunning(8)

        expect:
        line() == expected

        where:
        unicode | expected
        true    | "│███████▏▶·····················│» EXECUTING  ▶ 8  … 5,312 "
        false   | "[#######=......................]> EXECUTING  8 running, 5,312 queued "
        id = unicode ? "unicode" : "ascii"
    }

    def "fills the bar when more work is done than known (#id)"() {
        given:
        init(unicode, 2)
        3.times { progressBar.update(false) }

        expect:
        line() == expected

        where:
        unicode | expected
        true    | "│██████████████████████████████│» EXECUTING  ▶ 0  … 0 "
        false   | "[##############################]> EXECUTING  0 running, 0 queued "
        id = unicode ? "unicode" : "ascii"
    }

    def "fits the bar to a console #cols columns wide"() {
        given:
        init(true, 6560, cols)
        1240.times { progressBar.update(false) }
        progressBar.setRunning(8)

        expect:
        line(72000, true) == expected

        where:
        cols | expected
        100  | "│███████▏▶·····················│» EXECUTING  ▶ 8  … 5,312   [1m 12s]"
        60   | "│████▉▶···············│» EXECUTING  ▶ 8  … 5,312   [1m 12s]"
        50   | "│███▍▶··········│» EXECUTING  ▶ 8  … 5,312   [1m "
        0    | "│███████▏▶·····················│» EXECUTING  ▶ 8  … 5,312   [1m 12s]"
    }

    def "pulses the open edge every 500 ms"() {
        given:
        init(true)
        progressBar.setEdge(ProgressBar.Edge.PULSING)

        expect:
        styleOf(frame(0), "»") == StyledTextOutput.Style.Header
        styleOf(frame(500), "»") == StyledTextOutput.Style.Normal
        styleOf(frame(1000), "»") == StyledTextOutput.Style.Header
    }

    def "keeps a steady open edge bold"() {
        given:
        init(true)
        progressBar.setEdge(ProgressBar.Edge.STEADY)

        expect:
        styleOf(frame(0), "»") == StyledTextOutput.Style.Header
        styleOf(frame(500), "»") == StyledTextOutput.Style.Header
    }

    def "shows no open edge (#id)"() {
        given:
        init(unicode)
        progressBar.setPhase("WAITING")
        progressBar.setEdge(ProgressBar.Edge.NONE)

        expect:
        line() == expected

        where:
        unicode | expected
        true    | "│······························│ WAITING"
        false   | "[..............................] WAITING"
        id = unicode ? "unicode" : "ascii"
    }

    def "flags a grown total for 2.5 seconds (#id)"() {
        given:
        init(unicode, 10)
        frame(0)

        when:
        progressBar.moreProgress(5)

        then:
        line(100) == flagged
        line(2599) == flagged
        line(2600) == notFlagged

        where:
        unicode | flagged                                                           | notFlagged
        true    | "│······························│» EXECUTING  ▶ 0  … 15▲"          | "│······························│» EXECUTING  ▶ 0  … 15 "
        false   | "[..............................]> EXECUTING  0 running, 15 queued^" | "[..............................]> EXECUTING  0 running, 15 queued "
        id = unicode ? "unicode" : "ascii"
    }

    def "does not flag the first work"() {
        given:
        init(true)
        frame(0)

        when:
        progressBar.moreProgress(10)

        then:
        line(100) == "│······························│» EXECUTING  ▶ 0  … 10 "
    }

    def "restarts the flag when the total grows again"() {
        given:
        init(true, 10)
        frame(0)
        progressBar.moreProgress(5)
        frame(100)

        when:
        progressBar.moreProgress(5)
        frame(2000)

        then:
        line(4000) == "│······························│» EXECUTING  ▶ 0  … 20▲"
    }

    def "does not flag a shrinking total"() {
        given:
        init(true, 10)
        frame(0)

        when:
        progressBar.moreProgress(-5)

        then:
        line(100) == "│······························│» EXECUTING  ▶ 0  …  5 "
    }

    def "styles the segments and the counts"() {
        given:
        init(true, 10)
        3.times { progressBar.update(false) }
        progressBar.setRunning(2)

        expect:
        frame().collect { [it.style, it.text] } == [
            [StyledTextOutput.Style.Header, "│"],
            [StyledTextOutput.Style.SuccessHeader, "█████████"],
            [StyledTextOutput.Style.ProgressStatus, "▶▶▶▶▶▶"],
            [StyledTextOutput.Style.Header, "···············│» EXECUTING  "],
            [StyledTextOutput.Style.ProgressStatus, "▶"],
            [StyledTextOutput.Style.Header, " 2  … 5 "]
        ]
    }

    def "styles the done segment as failed after a failure"() {
        given:
        init(true, 10)
        progressBar.update(true)
        progressBar.update(false)

        expect:
        styleOf(frame(), "█") == StyledTextOutput.Style.FailureHeader
    }

    def "reports the drawn done share to the taskbar"() {
        given:
        init(true, 6560, 0, true)
        progressBar.update(failed)
        1239.times { progressBar.update(false) }
        progressBar.setRunning(8)

        expect:
        frame().first().text == sequence + "│"

        where:
        failed | sequence
        false  | "\u001B]9;4;1;23\u0007"
        true   | "\u001B]9;4;2;23\u0007"
    }

    def "does not report to the taskbar when not supported"() {
        given:
        init(true, 10, 0, false)
        progressBar.update(false)

        expect:
        !line().contains("\u001B]9;4;")
    }

    def "builds taskbar progress reset sequence when supported (#id)"() {
        given:
        def consoleMetaData = Stub(ConsoleMetaData) {
            supportsTaskbarProgress() >> supported
        }

        expect:
        ProgressBar.buildTaskbarProgressResetSequence(consoleMetaData) == resetSequence

        where:
        supported | resetSequence
        true      | "\u001B]9;4;0\u0007"
        false     | ""
        id = supported ? "supported" : "unsupported"
    }

    def "builds osc 9;4 sequence for command (#command)"() {
        expect:
        ProgressBar.buildOsc94Sequence(command) == expected

        where:
        command | expected
        "0"     | "\u001B]9;4;0\u0007"
        "1;10"  | "\u001B]9;4;1;10\u0007"
        "2;10"  | "\u001B]9;4;2;10\u0007"
    }
}
