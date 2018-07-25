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

import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import spock.lang.Specification
import spock.lang.Subject

class DefaultUserInputHandlerTest extends Specification {

    private static final String TEXT = 'Accept license?'
    def outputEventBroadcaster = Mock(OutputEventListener)
    def userInputReader = Mock(UserInputReader)
    @Subject def userInputHandler = new DefaultUserInputHandler(outputEventBroadcaster, userInputReader)

    def "can read sanitized input to yes/no question"() {
        when:
        def input = userInputHandler.askYesNoQuestion(TEXT)

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        1 * userInputReader.readInput() >> enteredUserInput
        input == sanitizedUserInput

        where:
        enteredUserInput | sanitizedUserInput
        null             | null
        'yes   '         | true
        'yes'            | true
        '   no   '       | false
        'y\u0000es '     | true
    }

    def "re-requests user input if invalid"() {
        when:
        def input = userInputHandler.askYesNoQuestion(TEXT)

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        0 * outputEventBroadcaster._
        1 * userInputReader.readInput() >> 'bla'
        1 * userInputReader.readInput() >> 'no'
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        input == false
    }
}
