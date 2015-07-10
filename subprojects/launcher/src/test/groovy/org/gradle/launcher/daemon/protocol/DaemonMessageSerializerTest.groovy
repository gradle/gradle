/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.daemon.protocol

import org.gradle.api.logging.LogLevel
import org.gradle.internal.progress.OperationIdentifier
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.logging.internal.LogEvent
import org.gradle.logging.internal.ProgressEvent

class DaemonMessageSerializerTest extends SerializerSpec {
    def serializer = DaemonMessageSerializer.create()

    def "can serialize BuildEvent messages"() {
        expect:
        def event = new BuildEvent(["a", "b", "c"])
        def result = usesEfficientSerialization(event, serializer)
        result instanceof BuildEvent
        result.payload == ["a", "b", "c"]
    }

    def "can serialize LogEvent messages"() {
        expect:
        def event = new LogEvent(1234, "category", LogLevel.LIFECYCLE, "message", new RuntimeException())
        def result = usesEfficientSerialization(event, serializer)
        result instanceof LogEvent
        result.timestamp == 1234
        result.category == "category"
        result.logLevel == LogLevel.LIFECYCLE
        result.message == "message"
        result.throwable.getClass() == event.throwable.getClass()
        result.throwable.message == event.throwable.message
        result.throwable.stackTrace == event.throwable.stackTrace

        def event2 = new LogEvent(1234, "category", LogLevel.LIFECYCLE, "message", null)
        def result2 = usesEfficientSerialization(event2, serializer)
        result2 instanceof LogEvent
        result2.timestamp == 1234
        result2.category == "category"
        result2.logLevel == LogLevel.LIFECYCLE
        result2.message == "message"
        result2.throwable == null
    }

    def "can serialize other messages"() {
        expect:
        def message = new CloseInput("id")
        def messageResult = serialize(message, serializer)
        messageResult instanceof CloseInput
        messageResult.identifier == "id"

        def event = new ProgressEvent(new OperationIdentifier(1234L), 3456L, "category", "all good")
        def eventResult = serialize(event, serializer)
        eventResult instanceof ProgressEvent
        eventResult.operationId.id == 1234L
        eventResult.timestamp == 3456L
        eventResult.category == "category"
        eventResult.status == "all good"
    }

    @Override
    def <T> T serialize(T value, Serializer<T> serializer) {
        return super.serialize(value, serializer)
    }
}
