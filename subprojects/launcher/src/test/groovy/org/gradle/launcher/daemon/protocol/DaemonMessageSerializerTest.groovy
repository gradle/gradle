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
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.internal.*
import org.gradle.messaging.remote.internal.PlaceholderException

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
        def result = serialize(event, serializer)
        result instanceof LogEvent
        result.timestamp == 1234
        result.category == "category"
        result.logLevel == LogLevel.LIFECYCLE
        result.message == "message"
        result.throwable.getClass() == event.throwable.getClass()
        result.throwable.message == event.throwable.message
        result.throwable.stackTrace == event.throwable.stackTrace

        def event2 = new LogEvent(1234, "category", LogLevel.LIFECYCLE, "message", null)
        def result2 = serialize(event2, serializer)
        result2 instanceof LogEvent
        result2.timestamp == 1234
        result2.category == "category"
        result2.logLevel == LogLevel.LIFECYCLE
        result2.message == "message"
        result2.throwable == null
    }

    def "can serialize StyledTextOutputEvent messages"() {
        expect:
        def event = new StyledTextOutputEvent(1234, "category", LogLevel.LIFECYCLE,
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Description, "description"),
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Error, "error"))
        def result = serialize(event, serializer)
        result instanceof StyledTextOutputEvent
        result.timestamp == 1234
        result.category == "category"
        result.logLevel == LogLevel.LIFECYCLE
        result.spans.size() == 2
        result.spans[0].style == StyledTextOutput.Style.Description
        result.spans[0].text == "description"
        result.spans[1].style == StyledTextOutput.Style.Error
        result.spans[1].text == "error"
    }

    def "can serialize LogLevelChangeEvent messages"() {
        expect:
        def event = new LogLevelChangeEvent(LogLevel.LIFECYCLE)
        def result = serialize(event, serializer)
        result instanceof LogLevelChangeEvent
        result.newLogLevel == LogLevel.LIFECYCLE
    }

    def "can serialize ProgressStartEvent messages"() {
        expect:
        def event = new ProgressStartEvent(new OperationIdentifier(1234L), new OperationIdentifier(5678L), 321L, "category", "description", "short", "header", "status")
        def result = serialize(event, serializer)
        result instanceof ProgressStartEvent
        result.operationId == new OperationIdentifier(1234L)
        result.parentId == new OperationIdentifier(5678L)
        result.timestamp == 321L
        result.category == "category"
        result.description == "description"
        result.shortDescription == "short"
        result.loggingHeader == "header"
        result.status == "status"

        def event2 = new ProgressStartEvent(new OperationIdentifier(1234L), null, 321L, "category", "description", null, null, "")
        def result2 = serialize(event2, serializer)
        result2 instanceof ProgressStartEvent
        result2.operationId == new OperationIdentifier(1234L)
        result2.parentId == null
        result2.timestamp == 321L
        result2.category == "category"
        result2.description == "description"
        result2.shortDescription == null
        result2.loggingHeader == null
        result2.status == ""
    }

    def "can serialize ProgressCompleteEvent messages"() {
        expect:
        def event = new ProgressCompleteEvent(new OperationIdentifier(1234L), 321L, "category", "description", "status")
        def result = serialize(event, serializer)
        result instanceof ProgressCompleteEvent
        result.operationId == new OperationIdentifier(1234L)
        result.timestamp == 321L
        result.category == "category"
        result.description == "description"
        result.status == "status"
    }

    def "can serialize ProgressEvent messages"() {
        expect:
        def event = new ProgressEvent(new OperationIdentifier(1234L), 321L, "category", "status")
        def result = serialize(event, serializer)
        result instanceof ProgressEvent
        result.operationId == new OperationIdentifier(1234L)
        result.timestamp == 321L
        result.category == "category"
        result.status == "status"
    }

    def "can serialize Failure messages"() {
        expect:
        def failure = new RuntimeException()
        def message = new Failure(failure)
        def result = serialize(message, serializer) // Throwable serialization is not more efficient than default
        result instanceof Failure
        result.value.getClass() == RuntimeException

        def unserializable = new IOException() {
            def thing = new Object()
        }
        def message2 = new Failure(unserializable)
        def result2 = serialize(message2, serializer)
        result2 instanceof Failure
        result2.value instanceof PlaceholderException
    }

    def "can serialize CloseInput messages"() {
        expect:
        def message = new CloseInput()
        def messageResult = usesEfficientSerialization(message, serializer)
        messageResult instanceof CloseInput
    }

    def "can serialize ForwardInput messages"() {
        expect:
        def message = new ForwardInput("greetings".bytes)
        def messageResult = usesEfficientSerialization(message, serializer)
        messageResult instanceof ForwardInput
        messageResult.bytes == message.bytes
    }

    def "can serialize other messages"() {
        expect:
        def message = new Cancel("id")
        def messageResult = serialize(message, serializer)
        messageResult instanceof Cancel
        messageResult.identifier == "id"
    }

    OutputEvent serialize(OutputEvent event, Serializer<Object> serializer) {
        def result = serialize(new OutputMessage(event), serializer)
        assert result instanceof OutputMessage
        return result.event
    }
}
