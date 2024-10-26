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

class BooleanQuestionPromptEventTest extends Specification {
    def "formats prompt"() {
        def event = new BooleanQuestionPromptEvent(timestampOf(123), "question?", true)

        expect:
        event.prompt == "question? (default: yes) [yes, no] " // trailing space
    }

    def "accepts valid input"() {
        def event = new BooleanQuestionPromptEvent(timestampOf(123), "question?", true)

        expect:
        def result = event.convert(input)
        result.response == expected
        result.newPrompt == null

        where:
        input      | expected
        'yes'      | true
        'y'        | true
        'Y'        | true
        'YES'      | true
        'yes   '   | true
        ' y   '    | true
        'no'       | false
        'n'        | false
        'N'        | false
        '   no   ' | false
        '   n   '  | false
    }

    def "uses default value on empty input"() {
        def event = new BooleanQuestionPromptEvent(timestampOf(123), "question?", true)

        expect:
        def result = event.convert("")
        result.response == true
        result.newPrompt == null
    }

    def "rejects invalid input"() {
        def event = new BooleanQuestionPromptEvent(timestampOf(123), "question?", true)

        expect:
        def result = event.convert(input)
        result.response == null
        result.newPrompt == "Please enter 'yes' or 'no' (default: 'yes'): "

        where:
        input  | _
        'bla'  | ''
        'nope' | ''
        'yep'  | ''
    }
}
