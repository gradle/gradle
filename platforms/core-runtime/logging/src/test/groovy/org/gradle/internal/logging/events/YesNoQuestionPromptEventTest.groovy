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

class YesNoQuestionPromptEventTest extends Specification {
    def "formats prompt"() {
        def event = new YesNoQuestionPromptEvent(timestampOf(123), "question?")

        expect:
        event.prompt == "question? [yes, no] " // trailing space
    }

    def "accepts valid input"() {
        def event = new YesNoQuestionPromptEvent(timestampOf(123), "question?")

        expect:
        def result = event.convert(input)
        result.response == expected
        result.newPrompt == null

        where:
        input   | expected
        "yes"   | true
        " yes " | true
        "no"    | false
        "no  "  | false
    }

    def "rejects invalid input"() {
        def event = new YesNoQuestionPromptEvent(timestampOf(123), "question?")

        expect:
        def result = event.convert(input)
        result.response == null
        result.newPrompt == "Please enter 'yes' or 'no': "

        where:
        input   | _
        ''      | _
        'bla'   | _
        'y'     | _
        'Y'     | _
        'ye'    | _
        'YES'   | _
        'n'     | _
        'N'     | _
        'NO'    | _
        'true'  | _
        'false' | _
    }
}
