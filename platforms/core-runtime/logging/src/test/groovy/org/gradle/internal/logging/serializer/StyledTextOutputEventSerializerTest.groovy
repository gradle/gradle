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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.serialize.Serializer
import spock.lang.Subject

@Subject(StyledTextOutputEventSerializer)
class StyledTextOutputEventSerializerTest extends LogSerializerSpec {
    StyledTextOutputEventSerializer serializer

    def setup() {
        BaseSerializerFactory serializerFactory = new BaseSerializerFactory()
        Serializer<LogLevel> logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class)
        serializer = new StyledTextOutputEventSerializer(new TimestampSerializer(),
                logLevelSerializer,
                new ListSerializer<StyledTextOutputEvent.Span>(
                        new SpanSerializer(serializerFactory.getSerializerFor(StyledTextOutput.Style.class))))
    }

    def "can serialize StyledTextOutputEvent messages"() {
        given:
        List spans = [new StyledTextOutputEvent.Span(StyledTextOutput.Style.Description, "description"),
                      new StyledTextOutputEvent.Span(StyledTextOutput.Style.Error, "error")]
        def event = new StyledTextOutputEvent(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, new OperationIdentifier(42L), spans)

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof StyledTextOutputEvent
        result.time == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.spans.size() == 2
        result.spans[0].style == StyledTextOutput.Style.Description
        result.spans[0].text == "description"
        result.spans[1].style == StyledTextOutput.Style.Error
        result.spans[1].text == "error"
        result.buildOperationId == new OperationIdentifier(42L)
    }

    def "can serialize StyledTextOutputEvent messages with null build operation id"() {
        given:
        def event = new StyledTextOutputEvent(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, null, "description")

        when:
        def result = serialize(event, serializer)

        then:
        result instanceof StyledTextOutputEvent
        result.time == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.spans.size() == 1
        result.spans[0].text == "description"
        result.buildOperationId == null
    }
}
