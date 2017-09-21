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

import org.apache.commons.lang.StringUtils
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.sink.OutputEventRenderer
import spock.lang.Specification
import spock.lang.Subject

class UserInputHandlerTest extends Specification {

    def outputEventRenderer = Mock(OutputEventRenderer)
    def userInputReader = Mock(UserInputReader)
    @Subject def userInputHandler = new UserInputHandler(outputEventRenderer, userInputReader)

    def "can check if user input is supported"() {
        when:
        boolean supported = userInputHandler.userInputSupported

        then:
        1 * userInputReader.supported >> true
        supported
    }

    def "can read user input"() {
        when:
        def input = userInputHandler.getUserResponse('Username')

        then:
        1 * outputEventRenderer.onOutput(_ as UserInputRequestEvent)
        1 * outputEventRenderer.onOutput(_ as UserInputResumeEvent)
        0 * outputEventRenderer._
        1 * userInputReader.readInput() >> enteredUserInput
        input == StringUtils.trim(enteredUserInput)

        where:
        enteredUserInput << ['foobar', 'Hello World', '   abc   ', '']
    }
}
