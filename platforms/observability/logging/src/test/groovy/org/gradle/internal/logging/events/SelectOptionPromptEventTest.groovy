/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.logging.events

import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class SelectOptionPromptEventTest extends Specification {
    def "formats prompt"() {
        def event = new SelectOptionPromptEvent(123, "question", ["11", "12", "13"], 1)

        assert event.prompt == TextUtil.toPlatformLineSeparators("""question:
  1: 11
  2: 12
  3: 13
Enter selection (default: 12) [1..3] """)
    }

    def "accepts valid input"() {
        def event = new SelectOptionPromptEvent(123, "question", ["1", "2", "3", "4"], 1)

        expect:
        def result = event.convert(input)
        result.response == expected
        result.newPrompt == null

        where:
        input | expected
        '1'   | 0
        '4'   | 3
        ' 1 ' | 0
    }

    def "uses default value on empty input"() {
        def event = new SelectOptionPromptEvent(123, "question", ["1", "2", "3", "4"], 1)

        expect:
        def result = event.convert("")
        result.response == 1
        result.newPrompt == null
    }

    def "rejects invalid input"() {
        def event = new SelectOptionPromptEvent(123, "question", ["1", "2", "3", "4"], 1)

        expect:
        def result = event.convert(input)
        result.response == null
        result.newPrompt == "Please enter a value between 1 and 4: "

        where:
        input | _
        'bla' | ''
        '1s'  | ''
        '0'   | ''
        '5'   | ''
        '-1'  | ''
    }
}
