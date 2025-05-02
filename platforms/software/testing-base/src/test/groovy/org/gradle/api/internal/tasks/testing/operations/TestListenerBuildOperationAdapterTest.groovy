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

package org.gradle.api.internal.tasks.testing.operations

import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.logging.SimpleTestOutputEvent
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.time.Time
import spock.lang.Specification

class TestListenerBuildOperationAdapterTest extends Specification {

    public static final int TEST_START_TIMESTAMP = 200
    public static final int TEST_COMPLETE_TIMESTAMP = 500

    BuildOperationListener listener = Mock()
    BuildOperationListenerManager listenerManager = Mock(BuildOperationListenerManager) {
        getBroadcaster() >> listener
    }

    BuildOperationIdFactory buildOperationIdFactory = Mock()
    TestListenerBuildOperationAdapter adapter = new TestListenerBuildOperationAdapter(Time.clock(), listenerManager, buildOperationIdFactory)
    TestDescriptorInternal parentTestDescriptorInternal = Mock()
    TestDescriptorInternal testDescriptorInternal = Mock()
    TestStartEvent testStartEvent = Mock()
    TestResult testResult = Mock()

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
        1 * listener.started(_, _) >> {
            generatedDescriptor = it[0]
            assert generatedDescriptor.details.testDescriptor == testDescriptorInternal
            assert generatedDescriptor.details.startTime == TEST_START_TIMESTAMP
        }

        when:
        adapter.completed(testDescriptorInternal, testResult, new TestCompleteEvent(TEST_COMPLETE_TIMESTAMP))
        then:
        1 * listener.finished(_, _) >> {
            assert generatedDescriptor == it[0] // started and finished descriptors are the same
            OperationFinishEvent finishEvent = it[1]
            assert finishEvent.startTime != TEST_START_TIMESTAMP
            assert finishEvent.endTime != TEST_COMPLETE_TIMESTAMP
            assert finishEvent.failure == null // not exposing test failures as operation failures
            assert finishEvent.result.result == testResult
        }
        0 * buildOperationIdFactory.nextId()
    }

    def "test output is exposed as progress"() {
        setup:
        long operationId = 1
        _ * buildOperationIdFactory.nextId() >> { operationId++ }
        TestOutputEvent testOutputEvent = new SimpleTestOutputEvent()

        BuildOperationDescriptor testOpDescriptor

        when:
        adapter.started(testDescriptorInternal, testStartEvent)

        and:
        adapter.output(testDescriptorInternal, testOutputEvent)

        then:
        listener.started(_, _) >> {
            testOpDescriptor = it[0]
        }

        1 * listener.progress(_, _) >> {
            assert testOpDescriptor.id == it[0]
            assert it[1].details.output == testOutputEvent
        }
    }
}
