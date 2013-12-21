/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal.protocol

import spock.lang.Specification
import org.gradle.messaging.remote.internal.Message

class AbstractPayloadMessageTest extends Specification {
    def "can get payload"() {
        def message = new TestPayloadMessage("payload")

        expect:
        message.nestedPayload == "payload"
    }

    def "can get nested payload"() {
        def nested = new TestPayloadMessage("payload")
        def message = new TestPayloadMessage(nested)

        expect:
        message.nestedPayload == "payload"
    }

    def "can create copy with new payload"() {
        def message = new TestPayloadMessage("payload")

        expect:
        def copy = message.withNestedPayload("new")
        copy instanceof TestPayloadMessage
        copy.nestedPayload == "new"
    }

    def "can create nested copy with new payload"() {
        def nested = new TestPayloadMessage("payload")
        def message = new TestPayloadMessage(nested)

        expect:
        def copy = message.withNestedPayload("new")
        copy instanceof TestPayloadMessage
        copy.payload instanceof  TestPayloadMessage
        copy.payload.payload == "new"
    }
}

class TestPayloadMessage extends AbstractPayloadMessage {
    final Object payload;

    TestPayloadMessage(Object payload) {
        this.payload = payload
    }

    @Override
    Message withPayload(Object payload) {
        return new TestPayloadMessage(payload)
    }
}