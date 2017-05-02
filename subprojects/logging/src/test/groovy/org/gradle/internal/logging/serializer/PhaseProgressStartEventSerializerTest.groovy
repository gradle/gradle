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
import org.gradle.internal.logging.events.PhaseProgressStartEvent
import spock.lang.Subject

@Subject(PhaseProgressStartEventSerializer)
class PhaseProgressStartEventSerializerTest extends LogSerializerSpec {
    private static final long TIMESTAMP = 42L
    private static final String CATEGORY = "category"
    private static final String DESCRIPTION = "description"
    private static final OperationIdentifier OPERATION_ID = new OperationIdentifier(1234L)

    PhaseProgressStartEventSerializer serializer = new PhaseProgressStartEventSerializer()

    def "can serialize PhaseProgressStartEvent messages"() {
        given:
        def event = new PhaseProgressStartEvent(OPERATION_ID, new OperationIdentifier(5678L), TIMESTAMP, CATEGORY, DESCRIPTION, "short", "header", "status", new OperationIdentifier(42L), new OperationIdentifier(41L), 1)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof PhaseProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == new OperationIdentifier(5678L)
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.shortDescription == "short"
        result.loggingHeader == "header"
        result.status == "status"
        result.buildOperationId == new OperationIdentifier(42L)
        result.parentBuildOperationId == new OperationIdentifier(41L)
        result.children == 1
    }

    def "can serialize PhaseProgressStartEvent messages with empty fields"() {
        given:
        def event = new PhaseProgressStartEvent(OPERATION_ID, null, TIMESTAMP, CATEGORY, DESCRIPTION, null, null, "", new OperationIdentifier(42L), null, 0)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof PhaseProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == null
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.shortDescription == null
        result.loggingHeader == null
        result.status == ""
        result.buildOperationId == new OperationIdentifier(42L)
        result.parentBuildOperationId == null
        result.children == 0
    }
}
