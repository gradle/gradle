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

    def setup() {
        progressBar = new ProgressBar(Stub(ConsoleMetaData), PREFIX, PROGRESS_BAR_WIDTH, SUFFIX, COMPLETE_CHAR as char, INCOMPLETE_CHAR as char, BUILD_PHASE, 0, 10)
    }

    private getProgress() {
        progressBar.formatProgress(false, 0).collect { it.text }.join("")
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
}
