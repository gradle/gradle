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

import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskProgressListener
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.events.test.*
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult
import org.gradle.tooling.internal.protocol.events.InternalTaskFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTaskProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalTaskStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult
import spock.lang.Specification

class BuildProgressListenerAdapterTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = new BuildProgressListenerAdapter([],[],[])

        then:
        adapter.subscribedOperations == []

        when:
        TestProgressListener listener = Mock()
        adapter = new BuildProgressListenerAdapter([listener],[],[])

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.TEST_EXECUTION]
    }

    def "adapter is only subscribing to task progress events if at least one task progress listener is attached"() {
        when:
        def adapter = new BuildProgressListenerAdapter([],[],[])

        then:
        adapter.subscribedOperations == []

        when:
        TaskProgressListener listener = Mock()
        adapter = new BuildProgressListenerAdapter([],[listener],[])

        then:
        adapter.subscribedOperations == [InternalTaskProgressListener.TASK_EXECUTION]
    }

    def "adapter can subscribe to both test and task progress events"() {
        when:
        def adapter = new BuildProgressListenerAdapter([],[],[])

        then:
        adapter.subscribedOperations == []

        when:
        adapter = new BuildProgressListenerAdapter([Mock(TestProgressListener)],[Mock(TaskProgressListener)],[])

        then:
        adapter.subscribedOperations as Set == [InternalBuildProgressListener.TEST_EXECUTION,InternalTaskProgressListener.TASK_EXECUTION] as Set
    }

    def "only TestProgressEventX instances are processed if a test listener is added"() {
        given:
        TestProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

        when:
        adapter.onEvent(new Object())

        then:
        0 * listener.statusChanged(_)
    }

   def "only TaskProgressEventX instances are processed if a task listener is added"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        adapter.onEvent(new Object())

        then:
        0 * listener.statusChanged(_)
    }

    def "only TestProgressEventX instances of known type are processed"() {
        given:
        TestProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

        when:
        def unknownEvent = Mock(InternalTestProgressEvent)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "only TaskProgressEventX instances of known type are processed"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def unknownEvent = Mock(InternalTaskProgressEvent)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same test descriptor exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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

    def "conversion of start events throws exception if previous start event with same task descriptor exists"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)
        adapter.onEvent(startEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('already available')
    }

    def "conversion of non-start events throws exception if no previous start event with same test descriptor exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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

    def "conversion of non-start events throws exception if no previous start event with same task descriptor exists"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ":dummy"
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def skippedEvent = Mock(InternalTaskFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(skippedEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "conversion of child events throws exception if no previous parent event exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

        when:
        def childTestDescriptor = Mock(InternalTestDescriptor)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> 1

        def childEvent = Mock(InternalTestStartedProgressEvent)
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTestDescriptor

        adapter.onEvent(childEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "conversion of child events expects parent event exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as StartEvent) >> { StartEvent event ->
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

    def "convert all InternalTaskDescriptor attributes"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ":someTask"
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getDisplayName() >> 'some task in human readable form'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as StartEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task started"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.displayName == 'some task in human readable form'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteStartedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as TestStartEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite started"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
        }
    }

    def "convert to TaskStartEvent"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task started"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSkippedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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

    def "convert to TaskSkippedEvent"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskSkippedResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.isUpToDate() >> true

        def skippedEvent = Mock(InternalTaskFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'task skipped'
        _ * skippedEvent.getDescriptor() >> taskDescriptor
        _ * skippedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { FinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task skipped"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskSkippedResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.upToDate == true
        }
    }

    def "convert to TestSuiteSucceededEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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

    def "convert to TaskSucceededEvent"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskSuccessResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        def succeededEvent = Mock(InternalTaskFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'task succeeded'
        _ * succeededEvent.getDescriptor() >> taskDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { FinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task succeeded"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to TestSuiteFailedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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

    def "convert to TaskFailedEvent"() {
        given:
        TaskProgressListener listener = Mock()
        def adapter = new BuildProgressListenerAdapter([],[listener],[])

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalTaskStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskFailureResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailure() >> Stub(InternalFailure)

        def failedEvent = Mock(InternalTaskFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'task failed'
        _ * failedEvent.getDescriptor() >> taskDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { FinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task failed"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    def "convert to TestStartedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as StartEvent) >> { StartEvent event ->
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
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener],[],[])

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
        1 * listener.statusChanged(_ as FinishEvent) >> { FinishEvent event ->
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

}
