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
import spock.lang.Subject

class NonInteractiveUserInputHandlerTest extends Specification {
    @Subject
    def userInputHandler = new NonInteractiveUserInputHandler()

    def "always returns null for yes/no question"() {
        expect:
        userInputHandler.askYesNoQuestion('Accept license?') == null
        userInputHandler.askUser { it.askYesNoQuestion('Accept license?') }.getOrNull() == null
    }

    def "always returns default for select question"() {
        expect:
        userInputHandler.selectOption('Select count', [1, 2, 3], 2) == 2
    }

    def "returns first option when no default"() {
        expect:
        userInputHandler.choice('Select count', [1, 2, 3])
            .ask() == 1
    }

    def "returns default option for choice"() {
        expect:
        userInputHandler.choice('Select count', [1, 2, 3])
            .defaultOption(2)
            .ask() == 2
        userInputHandler.choice('Select count', [1, 2, 3])
            .whenNotConnected(2)
            .ask() == 2
    }

    def "ignores option renderer"() {
        expect:
        userInputHandler.choice('Select count', [1, 2, 3])
            .renderUsing { throw new RuntimeException() }
            .ask() == 1
    }

    def "always returns default for int question"() {
        expect:
        userInputHandler.askIntQuestion('Enter something', 1, 2) == 2
    }

    def "always returns default for text question"() {
        expect:
        userInputHandler.askQuestion('Enter something', 'ok') == 'ok'
    }
}
