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
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestFailure
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
        result instanceof DefaultTestClassRunInfo
        result.testClassName == "some-test"
    }

    def "serializes CompositeId"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)

        when:
        def result = serialize(id)

        then:
        result instanceof CompositeIdGenerator.CompositeId
        result == id
    }

    def "serializes DefaultTestSuiteDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestSuiteDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result instanceof DefaultTestSuiteDescriptor
        result.id == id
        result.name == "some-test"
    }

    def "serializes WorkerTestSuiteDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new WorkerTestClassProcessor.WorkerTestSuiteDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result instanceof WorkerTestClassProcessor.WorkerTestSuiteDescriptor
        result.id == id
        result.name == "some-test"
    }

    def "serializes DefaultTestClassDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestClassDescriptor(id, "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result instanceof DefaultTestClassDescriptor
        result.id == id
        result.name == "some-test"
    }

    def "serializes DefaultTestDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestDescriptor(id, "some-class", "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result instanceof DefaultTestDescriptor
        result.id == id
        result.className == "some-class"
        result.name == "some-test"
    }

    def "serializes DefaultTestMethodDescriptor"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def descriptor = new DefaultTestMethodDescriptor(id, "some-class", "some-test")

        when:
        def result = serialize(descriptor)

        then:
        result instanceof DefaultTestMethodDescriptor
        result.id == id
        result.className == "some-class"
        result.name == "some-test"
    }

    def "serializes TestStartEvent"() {
        def id = new CompositeIdGenerator.CompositeId(1L, 2L)
        def event1 = new TestStartEvent(123L, id)
        def event2 = new TestStartEvent(456L)

        when:
        def result1 = serialize(event1)
        def result2 = serialize(event2)

        then:
        result1 instanceof TestStartEvent
        result1.parentId == id
        result1.startTime == 123L
        result2 instanceof TestStartEvent
        result2.parentId == null
        result2.startTime == 456L
    }

    def "serializes TestCompleteEvent"() {
        def event1 = new TestCompleteEvent(123L, TestResult.ResultType.SUCCESS)
        def event2 = new TestCompleteEvent(123L, null)

        when:
        def result1 = serialize(event1)
        def result2 = serialize(event2)

        then:
        result1 instanceof TestCompleteEvent
        result1.endTime == 123L
        result1.resultType == TestResult.ResultType.SUCCESS
        result2 instanceof TestCompleteEvent
        result2.endTime == 123L
        result2.resultType == null
    }

    def "serializes DefaultTestOutputEvent"() {
        def event = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, "hi")

        when:
        def result = serialize(event)

        then:
        result instanceof DefaultTestOutputEvent
        result.destination == TestOutputEvent.Destination.StdErr
        result.message == "hi"
    }

    def "serializes Throwable"() {
        def failure = new GradleException("broken", new RuntimeException("cause"))

        when:
        def result = serialize(failure, Throwable)

        then:
        result.class == GradleException
        result.message == "broken"
        result.cause.class == RuntimeException
        result.cause.message == "cause"
    }

    def "serializes TestFailure"() {
        TestFailure failure = TestFailure.fromTestAssertionFailure(new RuntimeException("cause"), 'expectedValue', 'actualValue')

        when:
        TestFailure result = serialize(failure, DefaultTestFailure)

        then:
        result.rawFailure.message == 'cause'
        result.details.assertionFailure == true
        result.details.message == 'cause'
        result.details.expected == 'expectedValue'
        result.details.actual == 'actualValue'
        result.details.stacktrace.contains('java.lang.RuntimeException: cause')
    }

    Object serialize(Object source, Class type = source.getClass()) {
        return super.serialize(source, serializer.build(type))
    }
}
