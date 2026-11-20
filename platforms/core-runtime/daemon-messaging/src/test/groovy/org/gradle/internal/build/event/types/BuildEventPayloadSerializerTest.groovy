/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.build.event.types

import org.gradle.internal.build.event.types.test.source.DefaultClassSource
import org.gradle.internal.build.event.types.test.source.DefaultClasspathResourceSource
import org.gradle.internal.build.event.types.test.source.DefaultDirectorySource
import org.gradle.internal.build.event.types.test.source.DefaultFileSource
import org.gradle.internal.build.event.types.test.source.DefaultMethodSource
import org.gradle.internal.build.event.types.test.source.DefaultNoSource
import org.gradle.internal.build.event.types.test.source.DefaultOtherSource
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.PhasedBuildActionResult
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import org.gradle.tooling.internal.provider.serialization.StreamedValue

import spock.lang.Shared

import java.time.Duration

class BuildEventPayloadSerializerTest extends SerializerSpec {
    def serializer = new BuildEventPayloadSerializer()

    @Shared
    def id = new OperationIdentifier(1)
    @Shared
    def parentId = new OperationIdentifier(2)
    @Shared
    def descriptor = new DefaultOperationDescriptor(id, "name", "display name", parentId)
    @Shared
    def plugin = new DefaultBinaryPluginIdentifier("plugin", "org.example.Plugin", "org.example")
    @Shared
    def taskDescriptor = new DefaultTaskDescriptor(id, ":a", ":a", "Task :a", parentId, [new DefaultTaskDescriptor(new OperationIdentifier(3), ":b", ":b", "Task :b", parentId, [] as Set, null)] as Set, plugin)
    @Shared
    def testDescriptor = new DefaultTestDescriptor(id, "test", "test()", "test", "ATOMIC", "suite", "Cls", "test", parentId, ":test", new DefaultMethodSource("Cls", "test"))
    @Shared
    def failure = new DefaultFailure("message", "description", [new DefaultFailure("cause", "cause description", [])])
    @Shared
    def problem = new DefaultProblemDetails(
        new DefaultProblemDefinition(new DefaultProblemId("id", "Id", new DefaultProblemGroup("group", "Group", null)), new DefaultSeverity(1), new DefaultDocumentationLink("https://example.com")),
        new DefaultDetails("details"),
        new DefaultContextualLabel("label"),
        [new DefaultFileLocation("a"), new DefaultLineInFileLocation("b", 1, 2, 3), new DefaultOffsetInFileLocation("c", 4, 5)],
        [new DefaultPluginIdLocation("org.example"), new DefaultTaskPathLocation(":a")],
        [new DefaultSolution("solution")],
        new DefaultInternalAdditionalData([key: "value"]),
        failure
    )
    @Shared
    def payload = new SerializedPayload([:], ["model".bytes])

    def "round-trips #value.class.simpleName"() {
        when:
        def result = serialize(value, serializer)

        then:
        result.class == value.class
        toBytes(result, serializer) == toBytes(value, serializer)

        where:
        value << [
            new DefaultOperationStartedProgressEvent(1, descriptor),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultSuccessResult(1, 2)),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultFailureResult(1, 2, [failure])),
            new DefaultOperationStartedProgressEvent(1, new DefaultRootOperationDescriptor(id, "root", "Root", null)),
            new DefaultOperationStartedProgressEvent(1, new DefaultBuildPhaseDescriptor(id, "phase", "Phase", parentId, "CONFIGURE_ROOT_BUILD", 3)),
            new DefaultOperationStartedProgressEvent(1, new DefaultProjectConfigurationDescriptor(id, "Configure", parentId, new File("root"), ":")),
            new DefaultOperationFinishedProgressEvent(1, new DefaultProjectConfigurationDescriptor(id, "Configure", parentId, new File("root"), ":"),
                new DefaultProjectConfigurationSuccessResult(1, 2, [new DefaultPluginApplicationResult(plugin, Duration.ofMillis(12)), new DefaultPluginApplicationResult(new DefaultScriptPluginIdentifier("script", new URI("file:/script.gradle")), Duration.ofNanos(5))])),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultProjectConfigurationFailureResult(1, 2, [failure], [])),
            new DefaultOperationStartedProgressEvent(1, new DefaultTransformDescriptor(id, "Transform", parentId, "transformer", "subject", [taskDescriptor] as Set)),
            new DefaultOperationStartedProgressEvent(1, new DefaultWorkItemDescriptor(id, "org.example.Action", "Work", parentId)),
            new DefaultOperationStartedProgressEvent(1, new DefaultFileDownloadDescriptor(id, "download", "Download", parentId, new URI("https://example.com/a.jar"))),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultFileDownloadSuccessResult(1, 2, 3)),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultFileDownloadFailureResult(1, 2, [failure], 3)),
            new DefaultOperationFinishedProgressEvent(1, descriptor, new NotFoundFileDownloadSuccessResult(1, 2)),
            new DefaultStatusEvent(1, new DefaultFileDownloadDescriptor(id, "download", "Download", parentId, new URI("https://example.com/a.jar")), 3, 4, "bytes"),
            new DefaultTaskStartedProgressEvent(1, taskDescriptor),
            new DefaultTaskFinishedProgressEvent(1, taskDescriptor, new DefaultTaskSuccessResult(1, 2, true, false, "UP-TO-DATE", true, ["reason"])),
            new DefaultTaskFinishedProgressEvent(1, taskDescriptor, new DefaultJavaCompileTaskSuccessResult(new DefaultTaskSuccessResult(1, 2, false, false, "succeeded", false, null), [new DefaultAnnotationProcessorResult("Processor", "ISOLATING", Duration.ofMillis(3))])),
            new DefaultTaskFinishedProgressEvent(1, taskDescriptor, new DefaultTaskFailureResult(1, 2, [failure], false, ["reason"])),
            new DefaultTaskFinishedProgressEvent(1, taskDescriptor, new DefaultTaskSkippedResult(1, 2, "SKIPPED", false)),
            new DefaultTestStartedProgressEvent(1, testDescriptor),
            new DefaultTestFinishedProgressEvent(1, testDescriptor, new DefaultTestSuccessResult(1, 2)),
            new DefaultTestFinishedProgressEvent(1, testDescriptor, new DefaultTestSkippedResult(1, 2)),
            new DefaultTestFinishedProgressEvent(1, testDescriptor, new DefaultTestFailureResult(1, 2, [
                new DefaultTestAssertionFailure("message", "description", [failure], "expected", "actual", "AssertionError", "stack"),
                new DefaultFileComparisonTestAssertionFailure("message", "description", [], "expected", "actual", "AssertionError", "stack", "e".bytes, "a".bytes),
                new DefaultTestFrameworkFailure("message", "description", [], "Error", "stack")
            ])),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", new DefaultClassSource("Cls"))),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", new DefaultFileSource(new File("a"), new DefaultFilePosition(1, null)))),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", new DefaultDirectorySource(new File("a")))),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", new DefaultClasspathResourceSource("a.feature", new DefaultFilePosition(1, 2)))),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", DefaultNoSource.getInstance())),
            new DefaultTestStartedProgressEvent(1, new DefaultTestDescriptor(id, "test", "test", "test", "SUITE", null, null, null, parentId, ":test", DefaultOtherSource.getInstance())),
            new DefaultTestOutputEvent(1, new DefaultTestOutputDescriptor(id, parentId), new DefaultTestOutputResult(1, 2, 1, "output")),
            new DefaultTestKeyValueDataMetadataEvent(1, new DefaultTestMetadataDescriptor(id, parentId), [key: "value"]),
            new DefaultTestFileAttachmentMetadataEvent(1, new DefaultTestMetadataDescriptor(id, parentId), new File("a.png"), "image/png"),
            new DefaultProblemEvent(1, new DefaultProblemDescriptor(id, parentId), problem),
            new DefaultProblemEvent(1, new DefaultProblemDescriptor(id, null), new DefaultProblemsSummariesDetails([new DefaultProblemSummary(new DefaultProblemId("id", "Id", new DefaultProblemGroup("group", "Group", null)), 3)])),
            new DefaultProblemEvent(1, new DefaultProblemDescriptor(id, null), new DefaultProblemDetails(
                new DefaultProblemDefinition(new DefaultProblemId("id", "Id", new DefaultProblemGroup("group", "Group", null)), new DefaultSeverity(1), null),
                null, null, [], [], [], new DefaultInternalPayloadSerializedAdditionalData("isolatable".bytes, payload), null)),
            new PhasedBuildActionResult(payload, PhasedActionResult.Phase.PROJECTS_LOADED),
            new StreamedValue(payload)
        ]
    }

    def "preserves the description and problems of a failure"() {
        when:
        def result = serialize(new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultFailureResult(1, 2, [DefaultFailure.fromThrowable(new RuntimeException("broken"))])), serializer)

        then:
        def resultFailure = result.result.failures[0]
        resultFailure.message == "broken"
        resultFailure.description.contains("java.lang.RuntimeException: broken")
        resultFailure.problems == []
    }

    def "writes an object shared within a payload once"() {
        given:
        def shared = new DefaultFailure("shared", "shared", [])
        def event = new DefaultOperationFinishedProgressEvent(1, descriptor, new DefaultFailureResult(1, 2, [
            new DefaultFailure("a", "a", [shared]),
            new DefaultFailure("b", "b", [shared])
        ]))

        when:
        def result = serialize(event, serializer)

        then:
        def failures = result.result.failures
        failures[0].causes[0].is(failures[1].causes[0])
    }

    def "refuses to write a payload of an unknown type"() {
        when:
        toBytes(["a", "b"], serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot serialize a build event payload of type java.util.ArrayList"
    }

    def "refuses to write additional data that is not a string"() {
        when:
        toBytes(new DefaultTestKeyValueDataMetadataEvent(1, new DefaultTestMetadataDescriptor(id, parentId), [key: 1]), serializer)

        then:
        thrown(IllegalArgumentException)
    }

    def "refuses to read an unknown type tag"() {
        given:
        def bytes = toBytes(new DefaultOperationStartedProgressEvent(1, descriptor), serializer)
        bytes[0] = (byte) 127

        when:
        fromBytes(bytes, serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected build event type tag 127"
    }

    def "refuses to read a value of the wrong type"() {
        given:
        def dependencies = [new DefaultSuccessResult(1, 2)] as Set
        def event = new DefaultTaskStartedProgressEvent(1, new DefaultTaskDescriptor(id, ":a", ":a", "Task :a", parentId, dependencies, null))

        when:
        fromBytes(toBytes(event, serializer), serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected org.gradle.internal.build.event.types.DefaultSuccessResult where org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor was expected"
    }

    def "refuses to read a dangling back reference"() {
        when:
        fromBytes([1, 0] as byte[], serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected back reference 0"
    }
}
