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

package org.gradle.internal.progress

import spock.lang.Specification
import spock.lang.Subject

@Subject(ProgressBar)
class ProgressBarTest extends Specification {
    public static final String INCOMPLETE_CHAR = ' '
    public static final String COMPLETE_CHAR = '#'
    public static final String SUFFIX = ']'
    public static final String PREFIX = '['
    public static final int PROGRESS_BAR_WIDTH = 10
    public static final String BUILD_PHASE = 'EXECUTING'

    ProgressBar progressBar

    def setup() {
        progressBar = new ProgressBar(PREFIX, PROGRESS_BAR_WIDTH, SUFFIX, COMPLETE_CHAR as char, INCOMPLETE_CHAR as char, BUILD_PHASE, 10)
    }

    def "formats progress bar"() {
        expect:
        progressBar.getProgress() == "$PREFIX${(INCOMPLETE_CHAR * PROGRESS_BAR_WIDTH)}$SUFFIX 0% $BUILD_PHASE".toString()
    }

    def "fills completed progress"() {
        when:
        progressBar.increment()

        then:
        progressBar.getProgress() == "[#         ] 10% EXECUTING"

        and:
        progressBar.incrementAndGetProgress() == "[##        ] 20% EXECUTING"
    }

    def "throws IllegalStateException if incremented past total"() {
        given:
        10.times { progressBar.increment() }

        when:
        progressBar.increment()

        then:
        thrown IllegalStateException
    }
}
