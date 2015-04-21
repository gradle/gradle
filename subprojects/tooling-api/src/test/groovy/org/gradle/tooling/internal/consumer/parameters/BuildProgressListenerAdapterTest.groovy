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

import org.gradle.tooling.events.FailureEvent
import org.gradle.tooling.events.SkippedEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.SuccessEvent
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.TestProgressListener
import org.gradle.tooling.internal.protocol.*
import spock.lang.Specification

class BuildProgressListenerAdapterTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = new BuildProgressListenerAdapter([])

        then:
        adapter.getSubscribedEvents() == []

        when:
        final TestProgressListener listener = Mock(TestProgressListener)
        adapter = new BuildProgressListenerAdapter([listener])

        then:
        adapter.getSubscribedEvents() == [BuildProgressListenerVersion1.TEST_PROGRESS]
    }

    def "only TestProgressEventVersionX instances are processed"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        adapter.onEvent(new Object())

        then:
        0 * listener.statusChanged(_)
    }

    def "only TestProgressEventVersionX instances of known type are processed"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def unknownEvent = Mock(TestProgressEventVersion1)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same test descriptor exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
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
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        def skippedEvent = Mock(TestFinishedProgressEventVersion1)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(skippedEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "conversion of child events throws exception if no previous parent event exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def childTestDescriptor = Mock(TestDescriptorVersion1)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> 1

        def childEvent = Mock(TestStartedProgressEventVersion1)
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
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def parentTestDescriptor = Mock(TestDescriptorVersion1)
        _ * parentTestDescriptor.getId() >> 1
        _ * parentTestDescriptor.getName() >> 'some parent'
        _ * parentTestDescriptor.getParentId() >> null

        def parentEvent = Mock(TestStartedProgressEventVersion1)
        _ * parentEvent.getEventTime() >> 999
        _ * parentEvent.getDescriptor() >> parentTestDescriptor

        def childTestDescriptor = Mock(TestDescriptorVersion1)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> parentTestDescriptor.getId()

        def childEvent = Mock(TestStartedProgressEventVersion1)
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTestDescriptor

        adapter.onEvent(parentEvent)
        adapter.onEvent(childEvent)

        then:
        notThrown(IllegalStateException)
    }

    def "convert to TestSuiteStartedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'test suite started'
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as StartEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite started"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.className == null
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSkippedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def result = Mock(TestResultVersion1)
        _ * result.resultType >> TestResultVersion1.RESULT_SKIPPED

        def skippedEvent = Mock(TestFinishedProgressEventVersion1)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'test suite skipped'
        _ * skippedEvent.getDescriptor() >> testDescriptor
        _ * skippedEvent.getResult() >> result

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as SkippedEvent) >> { SkippedEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite skipped"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSucceededEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getResultType() >> TestResultVersion1.RESULT_SUCCESSFUL

        def succeededEvent = Mock(TestFinishedProgressEventVersion1)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'test suite succeeded'
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as SuccessEvent) >> { SuccessEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite succeeded"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.className == null
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
        }
    }

    def "convert to TestSuiteFailedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_SUITE
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.resultType >> TestResultVersion1.RESULT_FAILED
        _ * testResult.getFailures() >> [Stub(FailureVersion1)]

        def failedEvent = Mock(TestFinishedProgressEventVersion1)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'test suite failed'
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as FailureEvent) >> { FailureEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test suite failed"
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.jvmTestKind == JvmTestKind.SUITE
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
            assert event.outcome.failures.size() == 1
        }
    }

    def "convert to TestStartedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_ATOMIC
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
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
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSkippedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_ATOMIC
        _ * testDescriptor.getParentId() >> null
        _ * testDescriptor.getClassName() >> 'Foo'

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def result = Mock(TestResultVersion1)
        _ * result.resultType >> TestResultVersion1.RESULT_SKIPPED

        def skippedEvent = Mock(TestFinishedProgressEventVersion1)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'test skipped'
        _ * skippedEvent.getDescriptor() >> testDescriptor
        _ * skippedEvent.getResult() >> result

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as SkippedEvent) >> { SkippedEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test skipped"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSucceededEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_ATOMIC
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.resultType >> TestResultVersion1.RESULT_SUCCESSFUL

        def succeededEvent = Mock(TestFinishedProgressEventVersion1)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getDisplayName() >> 'test succeeded'
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as SuccessEvent) >> { SuccessEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test succeeded"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
        }
    }

    def "convert to TestFailedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter([listener])

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getTestKind() >> JvmTestDescriptorVersion1.KIND_ATOMIC
        _ * testDescriptor.getParentId() >> null

        def startEvent = Mock(TestStartedProgressEventVersion1)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> [Stub(FailureVersion1)]
        _ * testResult.resultType >> TestResultVersion1.RESULT_FAILED

        def failedEvent = Mock(TestFinishedProgressEventVersion1)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'test failed'
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as FailureEvent) >> { FailureEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "test failed"
            assert event.descriptor.name == 'some test'
            assert event.descriptor.jvmTestKind == JvmTestKind.ATOMIC
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
            assert event.outcome.failures.size() == 1
        }
    }

}
