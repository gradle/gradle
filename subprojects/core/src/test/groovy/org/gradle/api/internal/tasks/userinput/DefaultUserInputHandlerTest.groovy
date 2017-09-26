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

import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.sink.OutputEventRenderer
import spock.lang.Specification
import spock.lang.Subject

class DefaultUserInputHandlerTest extends Specification {

    def outputEventRenderer = Mock(OutputEventRenderer)
    def userInputReader = Mock(UserInputReader)
    @Subject def userInputHandler = new DefaultUserInputHandler(outputEventRenderer, userInputReader)

    def "can read valid user input"() {
        when:
        def input = userInputHandler.getInput(new DefaultInputRequest('Enter username:'))

        then:
        1 * outputEventRenderer.onOutput(_ as UserInputRequestEvent)
        1 * outputEventRenderer.onOutput(_ as UserInputResumeEvent)
        0 * outputEventRenderer._
        1 * userInputReader.readInput() >> enteredUserInput
        input == sanitizedUserInput

        where:
        enteredUserInput | sanitizedUserInput
        null             | null
        'foobar'         | 'foobar'
        'Hello World'    | 'Hello World'
        '   abc   '      | 'abc'
        ''               | ''
        'ab\u0000cd'     | 'abcd'
    }

    def "re-requests user input if invalid"() {
        when:
        def input = userInputHandler.getInput(new TestInputRequest('Enter username:'))

        then:
        1 * outputEventRenderer.onOutput(_ as UserInputRequestEvent)
        0 * outputEventRenderer._
        1 * userInputReader.readInput() >> 'invalid'
        1 * userInputReader.readInput() >> 'valid'
        1 * outputEventRenderer.onOutput(_ as UserInputResumeEvent)
        input == 'valid'
    }

    def "can handle default value input"() {
        given:
        def defaultValue = 'foobar'

        when:
        def input = userInputHandler.getInput(new TestInputRequest('Enter username:', defaultValue))

        then:
        1 * outputEventRenderer.onOutput(_ as UserInputRequestEvent)
        1 * outputEventRenderer.onOutput(_ as UserInputResumeEvent)
        0 * outputEventRenderer._
        1 * userInputReader.readInput() >> ''
        input == defaultValue
    }

    private static class TestInputRequest implements InputRequest {

        private final String prompt
        private final String defaultValue

        TestInputRequest(String prompt) {
            this(prompt, null)
        }

        TestInputRequest(String prompt, String defaultValue) {
            this.prompt = prompt
            this.defaultValue = defaultValue
        }

        @Override
        String getPrompt() {
            "$prompt ($defaultValue)"
        }

        @Override
        boolean isValid(String input) {
            input == 'valid' ? true : false
        }

        @Override
        String getDefaultValue() {
            defaultValue
        }
    }
}
