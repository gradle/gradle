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

import org.gradle.StartParameter
import org.gradle.api.logging.LogLevel
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.launcher.cli.ExecuteBuildAction
import org.gradle.launcher.exec.DefaultBuildActionParameters

class DaemonMessageSerializerTest extends SerializerSpec {
    def serializer = DaemonMessageSerializer.create()

    def "can serialize BuildEvent messages"() {
        expect:
        def event = new BuildEvent(["a", "b", "c"])
        def result = usesEfficientSerialization(event, serializer)
        result instanceof BuildEvent
        result.payload == ["a", "b", "c"]
    }

    def "can serialize LogLevelChangeEvent messages"() {
        expect:
        def event = new LogLevelChangeEvent(LogLevel.LIFECYCLE)
        def result = serialize(event, serializer)
        result instanceof LogLevelChangeEvent
        result.newLogLevel == LogLevel.LIFECYCLE
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

    def "can serialize user input request event"() {
        expect:
        def event = new UserInputRequestEvent('prompt')
        def result = serialize(event, serializer)
        result instanceof UserInputRequestEvent
        result.prompt == 'prompt'
        result.logLevel == LogLevel.QUIET
    }

    def "can serialize user input resume event"() {
        expect:
        def event = new UserInputResumeEvent()
        def result = serialize(event, serializer)
        result instanceof UserInputResumeEvent
        result.logLevel == LogLevel.QUIET
    }

    def "can serialize Build message"() {
        expect:
        def action = new ExecuteBuildAction(new StartParameter())
        def clientMetadata = new GradleLauncherMetaData()
        def params = new DefaultBuildActionParameters([:], [:], new File("some-dir"), LogLevel.ERROR, true, false, false, ClassPath.EMPTY)
        def message = new Build(UUID.randomUUID(), [1, 2, 3] as byte[], action, clientMetadata, 1234L, params)
        Build result = serialize(message, serializer)
        result.identifier == message.identifier
        result.token == message.token
        result.startTime == message.startTime
        result.action
        result.buildRequestMetaData
        result.parameters
    }

    def "can serialize other messages"() {
        expect:
        def messageResult = serialize(message, serializer)
        messageResult.class == message.class

        where:
        message      | _
        new Cancel() | _
    }

    OutputEvent serialize(OutputEvent event, Serializer<Object> serializer) {
        def result = serialize(new OutputMessage(event), serializer)
        assert result instanceof OutputMessage
        return result.event
    }
}
