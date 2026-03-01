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

    private static final String BUILD_PHASE = 'EXECUTING'

    ProgressBar progressBar

    private getProgress() {
        progressBar.formatProgress(false, 0).collect { it.text }.join("")
    }

    private init(boolean unicode, int totalProgress = 10, boolean taskbarProgress = false) {
        if (unicode) {
            def consoleMetaData = Stub(ConsoleMetaData) {
                supportsUnicode() >> true
                supportsTaskbarProgress() >> taskbarProgress
            }
            progressBar = ProgressBar.getUnicodeProgressBar(consoleMetaData, BUILD_PHASE, totalProgress)
        } else {
            def consoleMetaData = Stub(ConsoleMetaData)
            progressBar = ProgressBar.getAsciiProgressBar(consoleMetaData, BUILD_PHASE, totalProgress)
        }
    }

    def "formats progress bar (#id)"() {
        given:
        init(unicode)

        expect:
        progress == rendering

        where:
        unicode            | rendering
        true               | "│···············│ 0% EXECUTING"
        false              | "[...............] 0% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "fills completed progress as work completes (#id)"() {
        given:
        init(unicode)

        when:
        progressBar.update(false)

        then:
        progress == rendering10

        when:
        progressBar.update(false)

        then:
        progress == rendering20

        when:
        8.times {
            progressBar.update(false)
        }

        then:
        progress == rendering100

        where:
        unicode            | rendering10                          | rendering20                         | rendering100
        true               | "│█▌·············│ 10% EXECUTING"    | "│███············│ 20% EXECUTING"   | "│███████████████│ 100% EXECUTING"
        false              | "[#..............] 10% EXECUTING"    | "[###............] 20% EXECUTING"   | "[###############] 100% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "gracefully handles excessively reported progress (#id)"() {
        given:
        init(unicode)

        when:
        10.times {
            progressBar.update(false)
        }

        then:
        progress == rendering100

        when:
        progressBar.update(false)

        then:
        progress == rendering110

        when:
        9.times {
            progressBar.update(false)
        }

        then:
        progress == rendering200

        where:
        unicode            | rendering100                         | rendering110                        | rendering200
        true               | "│███████████████│ 100% EXECUTING"   | "│██████████████·│ 110% EXECUTING"  | "│██████████████·│ 200% EXECUTING"
        false              | "[###############] 100% EXECUTING"   | "[##############.] 110% EXECUTING"  | "[##############.] 200% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "fills completed progress as work added (#id)"() {
        given:
        init(unicode)

        when:
        progressBar.update(false)
        progressBar.update(false)

        then:
        progress == rendering20

        when:
        progressBar.moreProgress(10)

        then:
        progress == rendering10

        where:
        unicode            | rendering10                          | rendering20
        true               | "│█▌·············│ 10% EXECUTING"    | "│███············│ 20% EXECUTING"
        false              | "[#..............] 10% EXECUTING"    | "[###............] 20% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "formats successful progress green (#id)"() {
        given:
        init(unicode)

        expect:
        progressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.SuccessHeader

        where:
        unicode            | _
        true               | _
        false              | _
        id = unicode ? "unicode" : "ascii"
    }

    def "formats failed progress red (#id)"() {
        given:
        init(unicode)

        when:
        progressBar.update(true)
        progressBar.update(false)

        then:
        progressBar.formatProgress(false, 0)[1].style == StyledTextOutput.Style.FailureHeader

        where:
        unicode            | _
        true               | _
        false              | _
        id = unicode ? "unicode" : "ascii"
    }

    def "unicode progress shows finer granularity (#id)"() {
        given:
        init(unicode, 80)

        when:
        progressBar.update(false) // 1/80 = 1.25%

        then:
        progress == rendering

        where:
        unicode            | rendering
        true               | "│▏··············│ 1% EXECUTING"
        false              | "[...............] 1% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "emits taskbar progress sequence when supported (#id)"() {
        given:
        init(unicode, 10, true)

        when:
        progressBar.update(false)

        then:
        progress == rendering

        where:
        unicode            | rendering
        true               | "\u001B]9;4;1;10\u0007│█▌·············│ 10% EXECUTING" // Should contain OSC 9;4 with state 1: ESC ] 9 ; 4 ; 2 ; 10 BEL
        false              | "[#..............] 10% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "emits error state in taskbar progress when failing (#id)"() {
        given:
        init(unicode, 10, true)

        when:
        progressBar.update(true) // Mark as failing

        then:
        progress == rendering

        where:
        unicode            | rendering
        true               | "\u001B]9;4;2;10\u0007│█▌·············│ 10% EXECUTING" // Should contain OSC 9;4 with state 2 (error): ESC ] 9 ; 4 ; 2 ; 10 BEL
        false              | "[#..............] 10% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }

    def "does not emit taskbar progress when not supported (#id)"() {
        given:
        init(unicode, 10, false)

        when:
        progressBar.update(false)

        then:
        !progress.contains('\u001B]9;4;') // Should NOT contain OSC 9;4 sequence
        progress == rendering

        where:
        unicode            | rendering
        true               | "│█▌·············│ 10% EXECUTING"
        false              | "[#..............] 10% EXECUTING"
        id = unicode ? "unicode" : "ascii"
    }
}
