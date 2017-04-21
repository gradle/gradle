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
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.serializer.LogEventSerializer
import org.gradle.internal.progress.OperationIdentifier
import spock.lang.Subject

@Subject(LogEventSerializer)
class LogEventSerializerTest extends SerializerSpec {
    LogEventSerializer serializer

    def setup() {
        BaseSerializerFactory serializerFactory = new BaseSerializerFactory()
        Serializer<LogLevel> logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class)
        Serializer<Throwable> throwableSerializer = serializerFactory.getSerializerFor(Throwable.class)
        Serializer<OperationIdentifier> operationIdentifierSerializer = serializerFactory.getSerializerFor(OperationIdentifier.class)
        serializer = new LogEventSerializer(logLevelSerializer, throwableSerializer, operationIdentifierSerializer)
    }

    def "can serialize LogEvent messages with failures and operation id"() {
        when:
        def event = new LogEvent.Builder(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, MESSAGE)
            .withThrowable(new RuntimeException())
            .forOperation(OPERATION_ID)
            .build()
        def result = serialize(event, serializer)

        then:
        result instanceof LogEvent
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.message == MESSAGE
        result.throwable.getClass() == event.throwable.getClass()
        result.throwable.message == event.throwable.message
        result.throwable.stackTrace == event.throwable.stackTrace
        result.buildOperationIdentifier == OPERATION_ID
    }

    def "can serialize LogEvent messages"() {
        when:
        def event = new LogEvent.Builder(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, MESSAGE).build()
        def result = serialize(event, serializer)

        then:
        result instanceof LogEvent
        result.timestamp == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.message == MESSAGE
        result.throwable == null
        result.buildOperationIdentifier == null
    }
}
