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

package org.gradle.tooling.internal.consumer.parameters

import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.*
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.*
import spock.lang.Specification

class BuildProgressListenerAdapterForTestOperationsTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.TEST_EXECUTION]
    }

    def "only TestProgressEventX instances are processed if a test listener is added"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        adapter.onEvent(Stub(InternalProgressEvent))

        then:
        0 * listener.statusChanged(_)
    }

    def "only TestProgressEventX instances of known type are processed"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def unknownEvent = Mock(InternalTestProgressEvent)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same test descriptor exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)
        adapter.onEvent(startEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('already available')
    }

    def "conversion of non-start events throws exception if no previous start event with same test descriptor exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        def skippedEvent = Mock(InternalTestFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(skippedEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "looking up parent operation throws exception if no previous event for parent operation exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def childTestDescriptor = Mock(InternalTestDescriptor)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> 1

        def childEvent = Mock(InternalTestStartedProgressEvent)
        _ * childEvent.getDisplayName() >> 'child event'
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTestDescriptor

        adapter.onEvent(childEvent)

        then:
        thrown(IllegalStateException)
    }

    def "conversion of child events expects parent event exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def parentTestDescriptor = Mock(InternalTestDescriptor)
        _ * parentTestDescriptor.getId() >> 1
        _ * parentTestDescriptor.getName() >> 'some parent'
        _ * parentTestDescriptor.getParentId() >> null

        def parentEvent = Mock(InternalTestStartedProgressEvent)
        _ * parentEvent.getEventTime() >> 999
        _ * parentEvent.getDescriptor() >> parentTestDescriptor

        def childTestDescriptor = Mock(InternalTestDescriptor)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> parentTestDescriptor.getId()

        def childEvent = Mock(InternalTestStartedProgressEvent)
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTestDescriptor

        adapter.onEvent(parentEvent)
        adapter.onEvent(childEvent)

        then:
        notThrown(IllegalStateException)
    }

    def "convert all InternalJvmTestDescriptor attributes"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getDisplayName() >> 'some test suite in human readable form'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_SUITE
        _ * testDescriptor.getSuiteName() >> 'some suite'
        _ * testDescriptor.getClassName() >> 'some class'
        _ * testDescriptor.getMethodName() >> 'some method'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'test suite started'
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TestStartEvent) >> { TestStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite started"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.displayName == 'some test suite in human readable form'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.suiteName == 'some suite'
            assert event.descriptor.className == 'some class'
            assert event.descriptor.methodName == 'some method'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'test suite started'
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TestStartEvent) >> { TestStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite started"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSkippedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestSkippedResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        def skippedEvent = Mock(InternalTestFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'test suite skipped'
        _ * skippedEvent.getDescriptor() >> testDescriptor
        _ * skippedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite skipped"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
            assert event.result instanceof TestSkippedResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to TestSuiteSucceededEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestSuccessResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        def succeededEvent = Mock(InternalTestFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'test suite succeeded'
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite succeeded"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
            assert event.result instanceof TestSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to TestSuiteFailedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestFailureResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> [Stub(InternalFailure)]

        def failedEvent = Mock(InternalTestFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'test suite failed'
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite failed"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
            assert event.result instanceof TestFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    def "convert to TestStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_ATOMIC
        _ * testDescriptor.getClassName() >> 'Foo'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'test started'
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TestStartEvent) >> { TestStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test started"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSkippedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_ATOMIC
        _ * testDescriptor.getClassName() >> 'Foo'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestSkippedResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        def skippedEvent = Mock(InternalTestFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'test skipped'
        _ * skippedEvent.getDescriptor() >> testDescriptor
        _ * skippedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test skipped"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
            assert event.result instanceof TestSkippedResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to TestSucceededEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_ATOMIC
        _ * testDescriptor.getClassName() >> 'Foo'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestSuccessResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        def succeededEvent = Mock(InternalTestFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'test succeeded'
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test succeeded"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
            assert event.result instanceof TestSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to TestFailedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def testDescriptor = Mock(InternalJvmTestDescriptor)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> InternalJvmTestDescriptor.KIND_ATOMIC
        _ * testDescriptor.getClassName() >> 'Foo'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTestStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(InternalTestFailureResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> [Stub(InternalFailure)]

        def failedEvent = Mock(InternalTestFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'test failed'
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as TestFinishEvent) >> { TestFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test failed"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
            assert event.result instanceof TestFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener testListener) {
        new BuildProgressListenerAdapter([(OperationType.TEST): [testListener]])
    }

}
