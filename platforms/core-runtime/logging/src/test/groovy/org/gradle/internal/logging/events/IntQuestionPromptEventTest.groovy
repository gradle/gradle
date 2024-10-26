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

import spock.lang.Specification

import static org.gradle.internal.time.TestTime.timestampOf

class IntQuestionPromptEventTest extends Specification {
    def "formats prompt"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", 2, 4)
        assert event.prompt == "enter value (min: 2, default: 4): "
    }

    def "accepts valid input"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", 2, 4)

        expect:
        def result = event.convert(input)
        result.response == expected
        result.newPrompt == null

        where:
        input  | expected
        '2'    | 2
        '4'    | 4
        '1209' | 1209
        ' 2 '  | 2
        Integer.MAX_VALUE.toString() | Integer.MAX_VALUE
    }

    def "uses default value on empty input"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", 2, 4)

        expect:
        def result = event.convert("")
        result.response == 4
        result.newPrompt == null
    }

    def "can have negative minimum value"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", -5, -2)

        expect:
        def result = event.convert(input)
        result.response == expected
        result.newPrompt == null

        where:
        input  | expected
        ''    | -2
        '-4'    | -4
        '-5'    | -5
    }

    def "rejects input that is not an integer"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", 2, 4)

        expect:
        def result = event.convert(input)
        result.response == null
        result.newPrompt == "Please enter an integer value (min: 2, default: 4): "

        where:
        input | _
        'bla' | ''
        '1s'  | ''
        '1 2' | ''
        Long.MAX_VALUE.toString() | ''
    }

    def "rejects input that is below minimum"() {
        def event = new IntQuestionPromptEvent(timestampOf(123), "question?", 2, 4)

        expect:
        def result = event.convert(input)
        result.response == null
        result.newPrompt == "Please enter an integer value >= 2 (default: 4): "

        where:
        input | _
        '0'   | ''
        '1'   | ''
        '-1'  | ''
    }
}
