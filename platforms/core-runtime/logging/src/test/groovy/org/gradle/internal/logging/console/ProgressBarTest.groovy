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

package org.gradle.internal.logging.console

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Specification
import spock.lang.Subject

@Subject(ProgressBar)
class ProgressBarTest extends Specification {
    public static final String INCOMPLETE_CHAR = '.'
    public static final String COMPLETE_CHAR = '#'
    public static final String SUFFIX = ']'
    public static final String PREFIX = '['
    public static final int PROGRESS_BAR_WIDTH = 10
    public static final String BUILD_PHASE = 'EXECUTING'

    ProgressBar progressBar
    ProgressBar unicodeProgressBar
    ProgressBar taskbarProgressBar
    ConsoleMetaData taskbarConsoleMetaData
    ConsoleMetaData unicodeConsoleMetaData

    def setup() {
        unicodeConsoleMetaData = Stub(ConsoleMetaData){
            supportsUnicode() >> true
        }
        progressBar = new ProgressBar(Stub(ConsoleMetaData), PREFIX, PROGRESS_BAR_WIDTH, SUFFIX, COMPLETE_CHAR as char, INCOMPLETE_CHAR as char, BUILD_PHASE, 0, 10)
        unicodeProgressBar = new ProgressBar(unicodeConsoleMetaData, '│', PROGRESS_BAR_WIDTH, '│', ' ' as char, ' ' as char, BUILD_PHASE, 0, 10)

        // Create a console metadata that supports taskbar progress
        taskbarConsoleMetaData = Stub(ConsoleMetaData) {
            supportsTaskbarProgress() >> true
        }
        taskbarProgressBar = new ProgressBar(taskbarConsoleMetaData, PREFIX, PROGRESS_BAR_WIDTH, SUFFIX, COMPLETE_CHAR as char, INCOMPLETE_CHAR as char, BUILD_PHASE, 0, 10)
    }

    private getProgress() {
        progressBar.formatProgress(false, 0).collect { it.text }.join("")
    }

    private getUnicodeProgress() {
        unicodeProgressBar.formatProgress(false, 0).collect { it.text }.join("")
    }

    def "formats progress bar"() {
        expect:
        progress == "[..........] 0% EXECUTING"
    }

    def "fills completed progress as work completes"() {
        when:
        progressBar.update(false)

        then:
        progress == "[#.........] 10% EXECUTING"

        when:
        progressBar.update(false)

        then:
        progress == "[##........] 20% EXECUTING"

        when:
        8.times {
            progressBar.update(false)
        }

        then:
        progress == "[##########] 100% EXECUTING"
    }

    def "gracefully handles excessively reported progress"() {
        when:
        10.times {
            progressBar.update(false)
        }

        then:
        progress == "[##########] 100% EXECUTING"

        when:
        progressBar.update(false)

        then:
        progress == "[#########.] 110% EXECUTING"

        when:
        9.times {
            progressBar.update(false)
        }

        then:
        progress == "[#########.] 200% EXECUTING"
    }

    def "fills completed progress as work added"() {
        when:
        progressBar.update(false)
        progressBar.update(false)

        then:
        progress == "[##........] 20% EXECUTING"

        when:
        progressBar.moreProgress(10)

        then:
        progress == "[#.........] 10% EXECUTING"
    }

    def "formats successful progress green"() {
        expect:
        progressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.SuccessHeader
    }

    def "formats failed progress red"() {
        when:
        progressBar.update(true)
        progressBar.update(false)

        then:
        progressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.FailureHeader
    }

    def "formats unicode progress bar"() {
        expect:
        unicodeProgress == "│··········│ 0% EXECUTING"
    }

    def "fills unicode progress with block characters"() {
        when:
        unicodeProgressBar.update(false)

        then:
        // 1/10 = 10% should show one full block (█)
        unicodeProgress == "│\u2588·········│ 10% EXECUTING"

        when:
        unicodeProgressBar.update(false)

        then:
        // 2/10 = 20% should show two full blocks
        unicodeProgress == "│\u2588\u2588········│ 20% EXECUTING"

        when:
        8.times {
            unicodeProgressBar.update(false)
        }

        then:
        // 10/10 = 100% should show all blocks filled
        unicodeProgress == "│\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588│ 100% EXECUTING"
    }

    def "unicode progress shows finer granularity"() {
        given:
        def fineGrainedProgressBar = new ProgressBar(unicodeConsoleMetaData, '│', 10, '│', ' ' as char, ' ' as char, BUILD_PHASE, 0, 80)

        when:
        fineGrainedProgressBar.update(false) // 1/80 = 1.25%

        then:
        // Should show a partial block character instead of empty
        def result = fineGrainedProgressBar.formatProgress(false, 0).collect { it.text }.join("")
        result == "│▏·········│ 1% EXECUTING"
    }

    def "unicode progress formats successful progress green"() {
        expect:
        unicodeProgressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.SuccessHeader
    }

    def "unicode progress formats failed progress red"() {
        when:
        unicodeProgressBar.update(true)
        unicodeProgressBar.update(false)

        then:
        unicodeProgressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.FailureHeader
    }

    def "emits taskbar progress sequence when supported"() {
        when:
        taskbarProgressBar.update(false)
        def result = taskbarProgressBar.formatProgress(false, 0).collect { it.text }.join("")

        then:
        // Should contain OSC 9;4 sequence: ESC ] 9 ; 4 ; 1 ; 10 BEL
        // ESC = \u001B, BEL = \u0007
        result.contains('\u001B]9;4;1;10\u0007')
        result.contains('[#.........] 10% EXECUTING')
    }

    def "emits error state in taskbar progress when failing"() {
        when:
        taskbarProgressBar.update(true) // Mark as failing
        def result = taskbarProgressBar.formatProgress(false, 0).collect { it.text }.join("")

        then:
        // Should contain OSC 9;4 with state 2 (error): ESC ] 9 ; 4 ; 2 ; 10 BEL
        result.contains('\u001B]9;4;2;10\u0007')
    }

    def "does not emit taskbar progress when not supported"() {
        when:
        progressBar.update(false)
        def result = progressBar.formatProgress(false, 0).collect { it.text }.join("")

        then:
        // Should NOT contain OSC 9;4 sequence
        !result.contains('\u001B]9;4;')
        result == '[#.........] 10% EXECUTING'
    }
}
