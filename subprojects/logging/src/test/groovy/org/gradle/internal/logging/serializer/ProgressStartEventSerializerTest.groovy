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

import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationCategory
import spock.lang.Subject

@Subject(ProgressStartEventSerializer)
class ProgressStartEventSerializerTest extends LogSerializerSpec {
    private static final long TIMESTAMP = 42L
    private static final String CATEGORY = "category"
    private static final String DESCRIPTION = "description"
    private static final OperationIdentifier OPERATION_ID = new OperationIdentifier(1234L)

    ProgressStartEventSerializer serializer

    def setup() {
        serializer = new ProgressStartEventSerializer()
    }

    def "can serialize ProgressStartEvent messages"(BuildOperationCategory category) {
        given:
        def event = new ProgressStartEvent(OPERATION_ID, new OperationIdentifier(5678L), TIMESTAMP, CATEGORY, DESCRIPTION, "short", "header", "status", 10, new OperationIdentifier(42L), new OperationIdentifier(43L), category)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == new OperationIdentifier(5678L)
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.shortDescription == "short"
        result.loggingHeader == "header"
        result.status == "status"
        result.totalProgress == 10
        result.buildOperationId == new OperationIdentifier(42L)
        result.parentBuildOperationId == new OperationIdentifier(43L)
        result.buildOperationCategory == category

        where:
        category << BuildOperationCategory.values()
    }

    def "can serialize ProgressStartEvent messages with empty fields"() {
        given:
        def event = new ProgressStartEvent(OPERATION_ID, null, TIMESTAMP, CATEGORY, DESCRIPTION, null, null, "", 0, null, null, BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == null
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.shortDescription == null
        result.loggingHeader == null
        result.status == ""
        result.totalProgress == 0
        result.buildOperationId == null
        result.parentBuildOperationId == null
        result.buildOperationCategory == BuildOperationCategory.UNCATEGORIZED
    }

    def "can serialize build operation ids with large long values"() {
        given:
        def event = new ProgressStartEvent(new OperationIdentifier(1_000_000_000_000L), null, TIMESTAMP, CATEGORY, DESCRIPTION, null, null, "", 0, new OperationIdentifier(42_000_000_000_000L), null, BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result.progressOperationId == new OperationIdentifier(1_000_000_000_000L)
        result.buildOperationId == new OperationIdentifier(42_000_000_000_000L)
    }
}
