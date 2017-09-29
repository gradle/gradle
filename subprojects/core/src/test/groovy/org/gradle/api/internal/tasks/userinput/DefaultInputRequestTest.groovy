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

class DefaultInputRequestTest extends Specification {

    private static final String TEXT = 'Please state your age.'

    def "throws exception if invalid prompt is provided"() {
        when:
        new DefaultInputRequest(prompt)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Text maybe not be null, empty or whitespace'

        where:
        prompt << [null, '', ' ']
    }

    def "can handle valid input"() {
        when:
        def inputRequest = new DefaultInputRequest(TEXT)

        then:
        inputRequest.text == TEXT
        inputRequest.prompt == TEXT
        inputRequest.isValid(input)

        where:
        input << ['any', '', ' ']
    }

    def "can handle invalid input"() {
        when:
        def inputRequest = new DefaultInputRequest(TEXT)

        then:
        inputRequest.text == TEXT
        inputRequest.prompt == TEXT
        !inputRequest.isValid(null)
    }
}
