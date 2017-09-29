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

package org.gradle.api.tasks.testing

import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.logging.SimpleTestOutputEvent
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.internal.progress.BuildOperationDescriptor
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationState
import org.gradle.internal.time.Clock
import spock.lang.Specification

class TestListenerBuildOperationAdapterTest extends Specification {

    public static final int TEST_START_TIMESTAMP = 200

    BuildOperationState rootOperation = Mock()
    BuildOperationListener listener = Mock()
    Clock clock = Mock()
    BuildOperationIdFactory buildOperationIdFactory = Mock()
    TestListenerBuildOperationAdapter adapter = new TestListenerBuildOperationAdapter(rootOperation, listener, buildOperationIdFactory, clock)
    TestDescriptorInternal parentTestDescriptorInternal = Mock()
    TestDescriptorInternal testDescriptorInternal = Mock()
    TestStartEvent testStartEvent = Mock()
    TestCompleteEvent testCompleteEvent = Mock()
    TestResult testResult = Mock()
    Object rootId = Mock()

    def setup() {
        _ * testDescriptorInternal.getParent() >> parentTestDescriptorInternal
    }

    def "tests are exposed"() {
        setup:
        testStartEvent.getStartTime() >> TEST_START_TIMESTAMP
        BuildOperationDescriptor generatedDescriptor;

        when:
        adapter.started(testDescriptorInternal, testStartEvent)

        then:
        1 * buildOperationIdFactory.nextId() >> 1
        1 * clock.currentTime >> 0
        1 * rootOperation.getId() >> rootId
        1 * listener.started(_, _) >> {
            generatedDescriptor = it[0]
            assert generatedDescriptor.operationType == BuildOperationCategory.TASK
            assert generatedDescriptor.details.testDescriptor == testDescriptorInternal
            assert generatedDescriptor.details.startTime == TEST_START_TIMESTAMP
        }

        when:
        adapter.completed(testDescriptorInternal, testResult, testCompleteEvent)
        then:
        1 * listener.finished(_, _) >> {
            assert generatedDescriptor == it[0] // started and finished descriptors are the same
            assert it[1].startTime == 0
            assert it[1].endTime == 500
            assert it[1].failure == null // not exposing test failures as operation failures
            assert it[1].result.result == testResult
        }
        1 * clock.currentTime >> 500
        0 * buildOperationIdFactory.nextId()
        0 * rootOperation.getId() >> rootId
    }

    def "test output is exposed as test child operation"() {
        setup:
        _ * clock.currentTime >> 0
        _ * rootOperation.getId() >> rootId
        long operationId = 1
        _ * buildOperationIdFactory.nextId() >> { operationId++ }
        TestOutputEvent testOutputEvent = new SimpleTestOutputEvent()

        BuildOperationDescriptor testOpDescriptor
        BuildOperationDescriptor outputOpDescriptor

        when:
        adapter.started(testDescriptorInternal, testStartEvent)
        and:
        adapter.output(testDescriptorInternal, testOutputEvent)

        then:
        listener.started(_, _) >> {
            testOpDescriptor = it[0]
        } >> {
            outputOpDescriptor = it[0]
            assert outputOpDescriptor.parentId == testOpDescriptor.id
        }

        1 * listener.finished(_, _) >> {
            assert outputOpDescriptor == it[0]
            assert it[1].result.output == testOutputEvent
        }
    }

    def "root test suite is not exposed"() {
        when:
        adapter.started(parentTestDescriptorInternal, testStartEvent)
        then:
        0 * listener._
        0 * buildOperationIdFactory._
        0 * clock._
        0 * rootOperation._

        when:
        adapter.completed(parentTestDescriptorInternal, testResult, testCompleteEvent)
        then:
        0 * listener._
        0 * buildOperationIdFactory._
        0 * clock._
        0 * rootOperation._
    }
}
