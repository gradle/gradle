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

import org.gradle.api.internal.provider.Providers
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class DefaultBuildScanUserInputHandlerTest extends Specification {

    def userInputHandler = Mock(UserInputHandler)
    def userQuestions = Mock(UserQuestions)
    @Subject def buildScanUserInputHandler = new DefaultBuildScanUserInputHandler(userInputHandler)

    def "can ask yes/no question and capture user input '#input'"() {
        given:
        def question = 'Accept license?'

        when:
        def answer = buildScanUserInputHandler.askYesNoQuestion(question)

        then:
        1 * userInputHandler.askUser(_) >> { Function f -> Providers.ofNullable(f.apply(userQuestions)) }
        1 * userQuestions.askYesNoQuestion(question) >> input
        answer == input

        where:
        input << [true, false, null]
    }
}
