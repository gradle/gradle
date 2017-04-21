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

package org.gradle.internal.serialize

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.serializer.SpanSerializer
import org.gradle.internal.logging.serializer.StyledTextOutputEventSerializer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.progress.OperationIdentifier
import spock.lang.Subject

@Subject(StyledTextOutputEventSerializer)
class StyledTextOutputEventSerializerTest extends SerializerSpec {
    StyledTextOutputEventSerializer serializer

    def setup() {
        BaseSerializerFactory serializerFactory = new BaseSerializerFactory()
        Serializer<LogLevel> logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class)
        Serializer<OperationIdentifier> operationIdentifierSerializer = serializerFactory.getSerializerFor(OperationIdentifier.class)
        serializer = new StyledTextOutputEventSerializer(
            logLevelSerializer,
            new ListSerializer<StyledTextOutputEvent.Span>(
                new SpanSerializer(serializerFactory.getSerializerFor(StyledTextOutput.Style.class))),
            operationIdentifierSerializer)
    }

    def "can serialize StyledTextOutputEvent messages"() {
        expect:
        List spans = [new StyledTextOutputEvent.Span(StyledTextOutput.Style.Description, "description"),
                      new StyledTextOutputEvent.Span(StyledTextOutput.Style.Error, "error")]
        def event = new StyledTextOutputEvent.Builder(TIMESTAMP, CATEGORY, spans)
            .withLogLevel(LogLevel.LIFECYCLE)
            .forOperation(OPERATION_ID)
            .build()
        def result = serialize(event, serializer)
        result instanceof StyledTextOutputEvent
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.spans.size() == 2
        result.spans[0].style == StyledTextOutput.Style.Description
        result.spans[0].text == "description"
        result.spans[1].style == StyledTextOutput.Style.Error
        result.spans[1].text == "error"
        result.buildOperationIdentifier == OPERATION_ID
    }
}
