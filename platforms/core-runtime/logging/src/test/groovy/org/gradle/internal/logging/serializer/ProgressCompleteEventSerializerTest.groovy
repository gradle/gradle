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

import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.TestTime
import org.gradle.internal.time.Timestamp
import spock.lang.Subject

@Subject(ProgressCompleteEventSerializer)
class ProgressCompleteEventSerializerTest extends LogSerializerSpec {
    private static final Timestamp TIMESTAMP = TestTime.timestampOf(42L)
    private static final String CATEGORY = "category"
    private static final OperationIdentifier OPERATION_ID = new OperationIdentifier(1234L)

    ProgressCompleteEventSerializer serializer = new ProgressCompleteEventSerializer(new TimestampSerializer())

    def "can serialize ProgressCompleteEvent messages"() {
        given:
        def event = new ProgressCompleteEvent(OPERATION_ID, TIMESTAMP, "status", true)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressCompleteEvent
        result.progressOperationId == OPERATION_ID
        result.time == TIMESTAMP
        result.status == "status"
        result.failed
    }

    def "can serialize ProgressCompleteEvent messages with empty fields"() {
        given:
        def event = new ProgressCompleteEvent(OPERATION_ID, TIMESTAMP, "", false)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressCompleteEvent
        result.progressOperationId == OPERATION_ID
        result.time == TIMESTAMP
        result.status == ""
        !result.failed
    }
}
