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

import org.gradle.tooling.TestProgressListener
import org.gradle.tooling.events.*
import org.gradle.tooling.internal.protocol.*
import spock.lang.Specification

class BuildProgressListenerAdapterTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = new BuildProgressListenerAdapter(Collections.emptyList())

        then:
        adapter.getSubscribedEvents() == []

        when:
        final TestProgressListener listener = Mock(TestProgressListener)
        adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        then:
        adapter.getSubscribedEvents() == Collections.singletonList(BuildProgressListenerVersion1.TEST_PROGRESS)
    }

    def "only TestProgressEventVersionX instances are processed"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        adapter.onEvent(new Object())

        then:
        0 * listener.statusChanged(_)
    }

    def "only TestProgressEventVersionX instances with known structure and outcome are processed"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        TestProgressEventVersion1 unknownEvent = Mock(TestProgressEventVersion1)
        _ * unknownEvent.getTestStructure() >> 'UNKNOWN_STRUCTURE'
        _ * unknownEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SKIPPED

        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)

        when:
        unknownEvent = Mock(TestProgressEventVersion1)
        _ * unknownEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * unknownEvent.getTestOutcome() >> 'UNKNOWN_OUTCOME'

        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)

        when:
        unknownEvent = Mock(TestProgressEventVersion1)
        _ * unknownEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * unknownEvent.getTestOutcome() >> 'UNKNOWN_OUTCOME'

        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same test descriptor exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
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
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 skippedEvent = Mock(TestProgressEventVersion1)
        _ * skippedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * skippedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SKIPPED
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(skippedEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')

        when:
        TestProgressEventVersion1 succeededEvent = Mock(TestProgressEventVersion1)
        _ * succeededEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * succeededEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SUCCEEDED
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(succeededEvent)

        then:
        e = thrown(IllegalStateException)
        e.message.contains('not available')

        when:
        TestProgressEventVersion1 failedEvent = Mock(TestProgressEventVersion1)
        _ * failedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * failedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_FAILED
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(failedEvent)

        then:
        e = thrown(IllegalStateException)
        e.message.contains('not available')

        when:
        skippedEvent = Mock(TestProgressEventVersion1)
        _ * skippedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * skippedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SKIPPED
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(skippedEvent)

        then:
        e = thrown(IllegalStateException)
        e.message.contains('not available')

        when:
        succeededEvent = Mock(TestProgressEventVersion1)
        _ * succeededEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * succeededEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SUCCEEDED
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(succeededEvent)

        then:
        e = thrown(IllegalStateException)
        e.message.contains('not available')

        when:
        failedEvent = Mock(TestProgressEventVersion1)
        _ * failedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * failedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_FAILED
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(failedEvent)

        then:
        e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "conversion of child events throws exception if no previous parent event exists"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def childTestDescriptor = Mock(TestDescriptorVersion1)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> 1

        TestProgressEventVersion1 childEvent = Mock(TestProgressEventVersion1)
        _ * childEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * childEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
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
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def parentTestDescriptor = Mock(TestDescriptorVersion1)
        _ * parentTestDescriptor.getId() >> 1
        _ * parentTestDescriptor.getName() >> 'some parent'
        _ * parentTestDescriptor.getParentId() >> null

        TestProgressEventVersion1 parentEvent = Mock(TestProgressEventVersion1)
        _ * parentEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * parentEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * parentEvent.getEventTime() >> 999
        _ * parentEvent.getDescriptor() >> parentTestDescriptor

        def childTestDescriptor = Mock(TestDescriptorVersion1)
        _ * childTestDescriptor.getId() >> 2
        _ * childTestDescriptor.getName() >> 'some child'
        _ * childTestDescriptor.getParentId() >> parentTestDescriptor.getId()

        TestProgressEventVersion1 childEvent = Mock(TestProgressEventVersion1)
        _ * childEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * childEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
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
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as ProgressEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.description == "TestSuite 'some test suite' started."
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.className == null
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSkippedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        TestProgressEventVersion1 skippedEvent = Mock(TestProgressEventVersion1)
        _ * skippedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * skippedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SKIPPED
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as SkippedEvent) >> { SkippedEvent event ->
            assert event.eventTime == 999
            assert event.description == "TestSuite 'some test suite' skipped."
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSuiteSucceededEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        TestProgressEventVersion1 succeededEvent = Mock(TestProgressEventVersion1)
        _ * succeededEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * succeededEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SUCCEEDED
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as SuccessEvent) >> { SuccessEvent event ->
            assert event.eventTime == 999
            assert event.description == "TestSuite 'some test suite' succeeded."
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.className == null
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
        }
    }

    def "convert to TestSuiteFailedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test suite'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> Collections.singletonList(Mock(FailureVersion1))

        TestProgressEventVersion1 failedEvent = Mock(TestProgressEventVersion1)
        _ * failedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_SUITE
        _ * failedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_FAILED
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as FailureEvent) >> { FailureEvent event ->
            assert event.eventTime == 999
            assert event.description == "TestSuite 'some test suite' failed."
            assert event.descriptor.name == 'some test suite'
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
            assert event.outcome.failures.size() == 1
        }
    }

    def "convert to TestStartedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as ProgressEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.description == "Test 'some test' started."
            assert event.descriptor.name == 'some test'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSkippedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(JvmTestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getParentId() >> null
        _ * testDescriptor.getClassName() >> 'Foo'

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        TestProgressEventVersion1 skippedEvent = Mock(TestProgressEventVersion1)
        _ * skippedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * skippedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SKIPPED
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> testDescriptor

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as SkippedEvent) >> { SkippedEvent event ->
            assert event.eventTime == 999
            assert event.description == "Test 'some test' skipped."
            assert event.descriptor.name == 'some test'
            assert event.descriptor.className == 'Foo'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TestSucceededEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2

        TestProgressEventVersion1 succeededEvent = Mock(TestProgressEventVersion1)
        _ * succeededEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * succeededEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_SUCCEEDED
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDescriptor() >> testDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as SuccessEvent) >> { SuccessEvent event ->
            assert event.eventTime == 999
            assert event.description == "Test 'some test' succeeded."
            assert event.descriptor.name == 'some test'
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
        }
    }

    def "convert to TestFailedEvent"() {
        given:
        final TestProgressListener listener = Mock(TestProgressListener)
        def adapter = new BuildProgressListenerAdapter(Collections.singletonList(listener))

        when:
        def testDescriptor = Mock(TestDescriptorVersion1)
        _ * testDescriptor.getId() >> 1
        _ * testDescriptor.getName() >> 'some test'
        _ * testDescriptor.getParentId() >> null

        TestProgressEventVersion1 startEvent = Mock(TestProgressEventVersion1)
        _ * startEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * startEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_STARTED
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> testDescriptor

        def testResult = Mock(TestResultVersion1)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> Collections.singletonList(Mock(FailureVersion1))

        TestProgressEventVersion1 failedEvent = Mock(TestProgressEventVersion1)
        _ * failedEvent.getTestStructure() >> TestProgressEventVersion1.STRUCTURE_ATOMIC
        _ * failedEvent.getTestOutcome() >> TestProgressEventVersion1.OUTCOME_FAILED
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDescriptor() >> testDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as FailureEvent) >> { FailureEvent event ->
            assert event.eventTime == 999
            assert event.description == "Test 'some test' failed."
            assert event.descriptor.name == 'some test'
            assert event.descriptor.parent == null
            assert event.outcome.startTime == 1
            assert event.outcome.endTime == 2
            assert event.outcome.failures.size() == 1
        }
    }

}
