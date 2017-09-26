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

package org.gradle.api.internal.tasks.userinput

import spock.lang.Specification

class MultipleChoiceInputRequestTest extends Specification {

    private static final String PROMPT = 'Accept license?'

    def "throws exception if invalid prompt is provided"() {
        when:
        new MultipleChoiceInputRequest(prompt, [])

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Prompt maybe not be null, empty or whitespace'

        where:
        prompt << [null, '', ' ']
    }

    def "throws exception if invalid choices are provided"() {
        when:
        new MultipleChoiceInputRequest(PROMPT, choices)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'At least two choices need to be provided'

        where:
        choices << [null, [], ['yes']]
    }

    def "can handle valid input"() {
        when:
        def inputRequest = new MultipleChoiceInputRequest(PROMPT, ['yes', 'no'])

        then:
        inputRequest.prompt == "$PROMPT [yes, no]"
        !inputRequest.defaultValue
        inputRequest.isValid(input)

        where:
        input << ['yes', 'no']
    }

    def "can handle invalid input"() {
        when:
        def inputRequest = new MultipleChoiceInputRequest(PROMPT, ['yes', 'no'])

        then:
        inputRequest.prompt == "$PROMPT [yes, no]"
        !inputRequest.defaultValue
        !inputRequest.isValid('other')
    }

    def "can provide default value"() {
        given:
        def defaultValue = 'no'

        when:
        def inputRequest = new MultipleChoiceInputRequest(PROMPT, ['yes', 'no'], defaultValue)

        then:
        inputRequest.prompt == "$PROMPT [yes, no] ($defaultValue)"
        inputRequest.defaultValue == defaultValue
    }
}
