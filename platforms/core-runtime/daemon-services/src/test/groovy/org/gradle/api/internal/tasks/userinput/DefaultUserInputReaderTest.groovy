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

package org.gradle.api.internal.tasks.userinput

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultUserInputReaderTest extends ConcurrentSpec {
    def userInputReader = new DefaultUserInputReader()

    def "blocks until user response received"() {
        expect:
        async {
            start {
                def result = userInputReader.readInput()
                instant.read
                assert result == response
            }
            thread.block()
            instant.put
            userInputReader.putInput(response)
        }
        instant.read > instant.put

        where:
        response << [
            new UserInputReader.TextResponse("answer"),
            UserInputReader.END_OF_INPUT
        ]
    }

    def "does not block after end of input received"() {
        given:
        userInputReader.putInput(UserInputReader.END_OF_INPUT)

        expect:
        def result = userInputReader.readInput()
        result == UserInputReader.END_OF_INPUT
    }
}
