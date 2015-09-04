/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.*
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.id.CompositeIdGenerator
import org.gradle.internal.serialize.SerializerSpec

class TestEventSerializerTest extends SerializerSpec {
    def serializer = TestEventSerializer.create()

    def "serializes DefaultTestClassRunInfo"() {
        def info = new DefaultTestClassRunInfo("some-test")

        when:
        def result = serialize(info)

        then:
        result.length == 1
        result[0] instanceof DefaultTestClassRunInfo
        result[0].testClassName == "some-test"
    }

    def "serializes CompositeId"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)

        when:
        def result = serialize(id)

        then:
        result.length == 1
        result[0] instanceof CompositeIdGenerator.CompositeId
        result[0] == id
    }

    def "serializes DefaultTestSuiteDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestSuiteDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result.length == 1
        result[0] instanceof DefaultTestSuiteDescriptor
        result[0].id == id
        result[0].name == "some-test"
    }

    def "serializes WorkerTestSuiteDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new WorkerTestClassProcessor.WorkerTestSuiteDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result.length == 1
        result[0] instanceof WorkerTestClassProcessor.WorkerTestSuiteDescriptor
        result[0].id == id
        result[0].name == "some-test"
    }

    def "serializes DefaultTestClassDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestClassDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result.length == 1
        result[0] instanceof DefaultTestClassDescriptor
        result[0].id == id
        result[0].name == "some-test"
    }

    def "serializes DefaultTestDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestDescriptor(id, "some-class", "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result.length == 1
        result[0] instanceof DefaultTestDescriptor
        result[0].id == id
        result[0].className == "some-class"
        result[0].name == "some-test"
    }

    def "serializes DefaultTestMethodDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestMethodDescriptor(id, "some-class", "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result.length == 1
        result[0] instanceof DefaultTestMethodDescriptor
        result[0].id == id
        result[0].className == "some-class"
        result[0].name == "some-test"
    }

    def "serializes TestStartEvent"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def event1 = new TestStartEvent(123L, id)
        def event2 = new TestStartEvent(456L)

        when:
        def result = serialize(event1, event2)

        then:
        result.length == 2
        result[0] instanceof TestStartEvent
        result[0].parentId == id
        result[0].startTime == 123L
        result[1] instanceof TestStartEvent
        result[1].parentId == null
        result[1].startTime == 456L
    }

    def "serializes TestCompleteEvent"() {
        def event1 = new TestCompleteEvent(123L, TestResult.ResultType.SUCCESS)
        def event2 = new TestCompleteEvent(123L, null)

        when:
        def result = serialize(event1, event2)

        then:
        result.length == 2
        result[0] instanceof TestCompleteEvent
        result[0].endTime == 123L
        result[0].resultType == TestResult.ResultType.SUCCESS
        result[1] instanceof TestCompleteEvent
        result[1].endTime == 123L
        result[1].resultType == null
    }

    def "serializes DefaultTestOutputEvent"() {
        def event = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, "hi")

        when:
        def result = serialize(event)

        then:
        result.length == 1
        result[0] instanceof DefaultTestOutputEvent
        result[0].destination == TestOutputEvent.Destination.StdErr
        result[0].message == "hi"
    }

    def "serializes Throwable"() {
        def failure = new GradleException("broken", new RuntimeException("cause"))

        when:
        def result = serialize(failure)

        then:
        result.length == 1
        result[0].class == GradleException
        result[0].message == "broken"
        result[0].cause.class == RuntimeException
        result[0].cause.message == "cause"
    }

    def Object[] serialize(Object... source) {
        return super.serialize(source, serializer)
    }
}
