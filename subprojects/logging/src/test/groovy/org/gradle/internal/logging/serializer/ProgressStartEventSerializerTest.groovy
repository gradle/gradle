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

import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
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
        def event = new ProgressStartEvent(OPERATION_ID, new OperationIdentifier(5678L), TIMESTAMP, CATEGORY, DESCRIPTION, "header", "status", 10, true, new OperationIdentifier(42L), category)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == new OperationIdentifier(5678L)
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.loggingHeader == "header"
        result.status == "status"
        result.totalProgress == 10
        result.buildOperationStart
        result.buildOperationId == new OperationIdentifier(42L)
        result.buildOperationCategory == category

        where:
        category << BuildOperationCategory.values()
    }

    def "can serialize ProgressStartEvent messages with empty fields"() {
        given:
        def event = new ProgressStartEvent(OPERATION_ID, null, TIMESTAMP, CATEGORY, DESCRIPTION, loggingHeader, "", 0, false, null, BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.progressOperationId == OPERATION_ID
        result.parentProgressOperationId == null
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.description == DESCRIPTION
        result.loggingHeader == loggingHeader
        result.status == ""
        result.totalProgress == 0
        !result.buildOperationStart
        result.buildOperationId == null
        result.buildOperationCategory == BuildOperationCategory.UNCATEGORIZED

        where:
        loggingHeader << [null, "logging", DESCRIPTION]
    }

    def "can serialize ProgressStartEvent messages where build and progress ids are the same"() {
        given:
        def event = new ProgressStartEvent(progressId, parentProgressId, TIMESTAMP, CATEGORY, DESCRIPTION, DESCRIPTION, "", 0, false, buildOperationId, BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.progressOperationId == progressId
        result.parentProgressOperationId == parentProgressId
        result.buildOperationId == buildOperationId

        where:
        progressId                 | parentProgressId           | buildOperationId
        new OperationIdentifier(1) | null                       | new OperationIdentifier(1)
        new OperationIdentifier(1) | null                       | new OperationIdentifier(2)
        new OperationIdentifier(1) | new OperationIdentifier(2) | new OperationIdentifier(1)
        new OperationIdentifier(1) | new OperationIdentifier(3) | new OperationIdentifier(2)
    }

    def "can serialize ProgressStartEvent messages where description ends with status or logging header"() {
        given:
        def event = new ProgressStartEvent(OPERATION_ID, new OperationIdentifier(5678L), TIMESTAMP, CATEGORY, description, loggingHeader, status, 10, true, new OperationIdentifier(42L), BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.description == description
        result.loggingHeader == loggingHeader
        result.status == status

        where:
        description       | status                | loggingHeader
        "the description" | "the description"     | "the description"
        "the description" | "description"         | "description"
        "the description" | "different"           | "different"
        "the description" | "not the description" | "not the description"
        "the description" | "description plus"    | "description plus"
        "the description" | "the"                 | "the"
        "the description" | ""                    | ""
        "the description" | ""                    | null
    }

    def "can serialize ProgressStartEvent with common categories"() {
        given:
        def event = new ProgressStartEvent(OPERATION_ID, new OperationIdentifier(5678L), TIMESTAMP, category, DESCRIPTION, "header", "status", 10, true, new OperationIdentifier(42L), BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof ProgressStartEvent
        result.category == category

        where:
        category << [ProgressStartEvent.BUILD_OP_CATEGORY, ProgressStartEvent.TASK_CATEGORY]
    }

    def "can serialize build operation ids with large long values"() {
        given:
        def event = new ProgressStartEvent(new OperationIdentifier(1_000_000_000_000L), null, TIMESTAMP, CATEGORY, DESCRIPTION, null, "", 0, true, new OperationIdentifier(42_000_000_000_000L), BuildOperationCategory.UNCATEGORIZED)

        when:
        def result = serialize(event, serializer)

        then:
        result.buildOperationStart
        result.progressOperationId == new OperationIdentifier(1_000_000_000_000L)
        result.buildOperationId == new OperationIdentifier(42_000_000_000_000L)
    }
}
