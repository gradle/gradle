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
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Serializer
import spock.lang.Subject

@Subject(LogEventSerializer)
class LogEventSerializerTest extends LogSerializerSpec {
    LogEventSerializer serializer

    def setup() {
        BaseSerializerFactory serializerFactory = new BaseSerializerFactory()
        Serializer<LogLevel> logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class)
        Serializer<Throwable> throwableSerializer = serializerFactory.getSerializerFor(Throwable.class)
        serializer = new LogEventSerializer(new TimestampSerializer(), logLevelSerializer, throwableSerializer)
    }

    def "can serialize LogEvent messages with failure and operation id"() {
        when:
        def event = new LogEvent(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, MESSAGE, new RuntimeException(), new OperationIdentifier(42L))
        def result = serialize(event, serializer)

        then:
        result instanceof LogEvent
        result.time == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.message == MESSAGE
        result.throwable.getClass() == event.throwable.getClass()
        result.throwable.message == event.throwable.message
        result.throwable.stackTrace == event.throwable.stackTrace
        result.buildOperationId == new OperationIdentifier(42L)
    }

    def "can serialize LogEvent messages"() {
        when:
        def event = new LogEvent(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, MESSAGE, null, null)
        def result = serialize(event, serializer)

        then:
        result instanceof LogEvent
        result.time == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.message == MESSAGE
        result.throwable == null
        result.buildOperationId == null
    }

    def "can serialize null value"() {
        when:
        def event = new LogEvent(TIMESTAMP, CATEGORY, LogLevel.LIFECYCLE, null, null, null)
        def result = serialize(event, serializer)

        then:
        result instanceof LogEvent
        result.time == TIMESTAMP
        result.category == CATEGORY
        result.logLevel == LogLevel.LIFECYCLE
        result.message == null
        result.throwable == null
        result.buildOperationId == null
    }
}
