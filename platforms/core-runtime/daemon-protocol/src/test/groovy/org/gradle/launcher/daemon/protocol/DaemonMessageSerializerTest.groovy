/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.events.BooleanQuestionPromptEvent
import org.gradle.internal.logging.events.IntQuestionPromptEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.internal.logging.events.SelectOptionPromptEvent
import org.gradle.internal.logging.events.TextQuestionPromptEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.events.YesNoQuestionPromptEvent
import org.gradle.internal.serialize.DefaultSerializer
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.launcher.exec.DefaultBuildActionParameters
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class DaemonMessageSerializerTest extends SerializerSpec {
    def serializer = DaemonMessageSerializer.create(new DefaultSerializer<BuildAction>())

    def "can serialize BuildEvent messages"() {
        expect:
        def event = new BuildEvent(["a", "b", "c"])
        def result = serialize(event, serializer)
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

    def "can serialize Success message"() {
        expect:
        def message = new Success("result")
        def result = serialize(message, serializer)
        result instanceof Success
        result.value == "result"

        def message2 = new Success(null)
        def result2 = serialize(message2, serializer)
        result2 instanceof Success
        result2.value == null
    }

    def "can serialize Success message with BuildActionResult payload"() {
        expect:
        def buildSuccessful = BuildActionResult.of(new SerializedPayload(null, []))
        def message = new Success(buildSuccessful)
        def result = serialize(message, serializer)
        result instanceof Success
        result.value instanceof BuildActionResult
        !result.value.wasCancelled()
        result.value.result.header == null
        result.value.result.serializedModel.empty
        result.value.failure == null
        result.value.exception == null

        def buildResult = BuildActionResult.of(new SerializedPayload("header", ["hi".bytes]))
        def message2 = new Success(buildResult)
        def result2 = serialize(message2, serializer)
        result2 instanceof Success
        result2.value instanceof BuildActionResult
        !result2.value.wasCancelled()
        result2.value.result.header == "header"
        result2.value.result.serializedModel.size() == 1
        result2.value.failure == null
        result2.value.exception == null

        def buildFailed = BuildActionResult.failed(new RuntimeException("broken"))
        def message3 = new Success(buildFailed)
        def result3 = serialize(message3, serializer)
        result3 instanceof Success
        result3.value instanceof BuildActionResult
        !result3.value.wasCancelled()
        result3.value.result == null
        result3.value.failure == null
        result3.value.exception instanceof RuntimeException

        def buildCancelled = BuildActionResult.cancelled(new RuntimeException("broken"))
        def message4 = new Success(buildCancelled)
        def result4 = serialize(message4, serializer)
        result4 instanceof Success
        result4.value instanceof BuildActionResult
        result4.value.result == null
        result4.value.failure == null
        result4.value.exception instanceof RuntimeException

        def buildFailedWithSerializedFailure = BuildActionResult.failed(new SerializedPayload("header", ["hi".bytes]))
        def message5 = new Success(buildFailedWithSerializedFailure)
        def result5 = serialize(message5, serializer)
        result5 instanceof Success
        result5.value instanceof BuildActionResult
        !result5.value.wasCancelled()
        result5.value.result == null
        result5.value.failure != null
        result5.value.exception == null
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

    def "can serialize Finished messages"() {
        expect:
        def message = new Finished()
        def messageResult = serialize(message, serializer)
        messageResult instanceof Finished
    }

    def "can serialize CloseInput messages"() {
        expect:
        def message = new CloseInput()
        def messageResult = serialize(message, serializer)
        messageResult instanceof CloseInput
    }

    def "can serialize ForwardInput messages"() {
        expect:
        def message = new ForwardInput("greetings".bytes)
        def messageResult = serialize(message, serializer)
        messageResult instanceof ForwardInput
        messageResult.bytes == message.bytes
    }

    def "can serialize UserResponse messages"() {
        expect:
        def message = new UserResponse("greetings")
        def messageResult = serialize(message, serializer)
        messageResult instanceof UserResponse
        messageResult.response == message.response
    }

    def "can serialize user input request event"() {
        expect:
        def event = new UserInputRequestEvent()
        def result = serialize(event, serializer)
        result instanceof UserInputRequestEvent
    }

    def "can serialize yes-no question prompt event"() {
        expect:
        def event = new YesNoQuestionPromptEvent(123, 'prompt')
        def result = serialize(event, serializer)
        result instanceof YesNoQuestionPromptEvent
        result.question == 'prompt'
        result.timestamp == 123
    }

    def "can serialize boolean question prompt event"() {
        expect:
        def event = new BooleanQuestionPromptEvent(123, 'prompt', false)
        def result = serialize(event, serializer)
        result instanceof BooleanQuestionPromptEvent
        result.question == 'prompt'
        !result.defaultValue
        result.timestamp == 123
    }

    def "can serialize int question prompt event"() {
        expect:
        def event = new IntQuestionPromptEvent(123, 'prompt', 1, 2)
        def result = serialize(event, serializer)
        result instanceof IntQuestionPromptEvent
        result.question == 'prompt'
        result.minValue == 1
        result.defaultValue == 2
        result.timestamp == 123
    }

    def "can serialize text question prompt event"() {
        expect:
        def event = new TextQuestionPromptEvent(123, 'prompt', 'value')
        def result = serialize(event, serializer)
        result instanceof TextQuestionPromptEvent
        result.question == 'prompt'
        result.defaultValue == 'value'
        result.timestamp == 123
    }

    def "can serialize select option prompt event"() {
        expect:
        def event = new SelectOptionPromptEvent(123, 'prompt', ['a', 'b'], 1)
        def result = serialize(event, serializer)
        result instanceof SelectOptionPromptEvent
        result.question == 'prompt'
        result.options == ['a', 'b']
        result.defaultOption == 1
        result.timestamp == 123
    }

    def "can serialize user input resume event"() {
        expect:
        def event = new UserInputResumeEvent(123)
        def result = serialize(event, serializer)
        result instanceof UserInputResumeEvent
        result.timestamp == 123
    }

    def "can serialize read stdin event"() {
        expect:
        def event = new ReadStdInEvent()
        def result = serialize(event, serializer)
        result instanceof ReadStdInEvent
    }

    def "can serialize Build message"() {
        expect:
        def action = new TestAction()
        def clientMetadata = new GradleLauncherMetaData()
        def params = new DefaultBuildActionParameters([:], [:], new File("some-dir"), LogLevel.ERROR, true, ClassPath.EMPTY)
        def message = new Build(UUID.randomUUID(), [1, 2, 3] as byte[], action, clientMetadata, 1234L, true, params)
        def result = serialize(message, serializer)
        result instanceof Build
        result.identifier == message.identifier
        result.token == message.token
        result.startTime == message.startTime
        result.interactive
        result.action
        result.buildClientMetaData
        result.parameters
    }

    def "can serialize DaemonUnavailable message"() {
        expect:
        def message = new DaemonUnavailable("reason")
        def result = serialize(message, serializer)
        result instanceof DaemonUnavailable
        result.reason == "reason"
    }

    def "can serialize Cancel message"() {
        expect:
        def message = new Cancel()
        def result = serialize(message, serializer)
        result instanceof Cancel
    }

    def "can serialize BuildStarted message"() {
        expect:
        def diagnostics = new DaemonDiagnostics(new File("log"), 1234L)
        def message = new BuildStarted(diagnostics)
        def result = serialize(message, serializer)
        result instanceof BuildStarted
        result.diagnostics.daemonLog == message.diagnostics.daemonLog
        result.diagnostics.pid == message.diagnostics.pid

        def diagnostics2 = new DaemonDiagnostics(new File("log"), null)
        def message2 = new BuildStarted(diagnostics2)
        def result2 = serialize(message2, serializer)
        result2 instanceof BuildStarted
        result2.diagnostics.daemonLog == message2.diagnostics.daemonLog
        result2.diagnostics.pid == null
    }

    def "can serialize other messages"() {
        expect:
        def messageResult = serialize(message, serializer)
        messageResult.class == message.class

        where:
        message                                                  | _
        new Stop(UUID.randomUUID(), [1, 2, 3] as byte[])         | _
        new StopWhenIdle(UUID.randomUUID(), [1, 2, 3] as byte[]) | _
        new ReportStatus(UUID.randomUUID(), [1, 2, 3] as byte[]) | _
    }

    OutputEvent serialize(OutputEvent event, Serializer<Object> serializer) {
        def result = serialize(new OutputMessage(event), serializer)
        assert result instanceof OutputMessage
        return result.event
    }

    private static class TestAction implements BuildAction, Serializable {
        @Override
        StartParameterInternal getStartParameter() {
            return null
        }

        @Override
        boolean isRunTasks() {
            return false
        }

        @Override
        boolean isCreateModel() {
            return false
        }
    }
}
