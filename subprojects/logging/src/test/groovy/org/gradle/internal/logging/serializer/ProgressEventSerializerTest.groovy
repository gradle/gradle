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

import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressEvent
import spock.lang.Subject

@Subject(ProgressEventSerializer)
class ProgressEventSerializerTest extends LogSerializerSpec {
    private static final long TIMESTAMP = 42L
    private static final String CATEGORY = "category"
    private static final OperationIdentifier OPERATION_ID = new OperationIdentifier(1234L)

    ProgressEventSerializer serializer = new ProgressEventSerializer()

    def "can serialize ProgressEvent messages"() {
        given:
        def event = new ProgressEvent(OPERATION_ID, TIMESTAMP, "category", "status")

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressEvent
        result.progressOperationId == OPERATION_ID
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.status == "status"
    }

    def "can serialize ProgressEvent messages with empty fields"() {
        given:
        def event = new ProgressEvent(OPERATION_ID, TIMESTAMP, "category", "")

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressEvent
        result.progressOperationId == OPERATION_ID
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.status == ""
    }
}
