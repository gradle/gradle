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

package org.gradle.internal.logging.serializer


import org.gradle.internal.logging.events.UserInputRequestEvent
import spock.lang.Subject

class UserInputRequestEventSerializerTest extends LogSerializerSpec {

    @Subject def serializer = new UserInputRequestEventSerializer()

    def "can serialize user input event"() {
        given:
        def event = new UserInputRequestEvent()

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof UserInputRequestEvent
    }
}
