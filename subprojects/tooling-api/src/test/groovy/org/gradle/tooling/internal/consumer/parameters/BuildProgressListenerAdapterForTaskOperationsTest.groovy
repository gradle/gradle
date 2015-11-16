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

import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.*
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.*
import spock.lang.Specification

class BuildProgressListenerAdapterForTaskOperationsTest extends Specification {

    def "adapter is only subscribing to task progress events if at least one task progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.TASK_EXECUTION]
    }

    def "only TaskProgressEventX instances are processed if a task listener is added"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        adapter.onEvent(Mock(InternalProgressEvent))

        then:
        0 * listener.statusChanged(_)
    }

    def "only TaskProgressEventX instances of known type are processed"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def unknownEvent = Mock(InternalProgressEvent)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same task descriptor exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)
        adapter.onEvent(startEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('already available')
    }

    def "conversion of non-start events throws exception if no previous start event with same task descriptor exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ":dummy"
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null

        def skippedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDescriptor() >> taskDescriptor

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
        def childTaskDescriptor = Mock(InternalTaskDescriptor)
        _ * childTaskDescriptor.getId() >> 2
        _ * childTaskDescriptor.getName() >> 'some child'
        _ * childTaskDescriptor.getParentId() >> 1

        def childEvent = Mock(InternalOperationStartedProgressEvent)
        _ * childEvent.getDisplayName() >> 'child event'
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTaskDescriptor

        adapter.onEvent(childEvent)

        then:
        thrown(IllegalStateException)
    }

    def "conversion of child events expects parent event exists"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def parentTaskDescriptor = Mock(InternalTaskDescriptor)
        _ * parentTaskDescriptor.getId() >> 1
        _ * parentTaskDescriptor.getName() >> 'some parent'
        _ * parentTaskDescriptor.getParentId() >> null

        def parentEvent = Mock(InternalOperationStartedProgressEvent)
        _ * parentEvent.getEventTime() >> 999
        _ * parentEvent.getDescriptor() >> parentTaskDescriptor

        def childTaskDescriptor = Mock(InternalTaskDescriptor)
        _ * childTaskDescriptor.getId() >> 2
        _ * childTaskDescriptor.getName() >> 'some child'
        _ * childTaskDescriptor.getParentId() >> parentTaskDescriptor.getId()

        def childEvent = Mock(InternalOperationStartedProgressEvent)
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTaskDescriptor

        adapter.onEvent(parentEvent)
        adapter.onEvent(childEvent)

        then:
        notThrown(IllegalStateException)
    }

    def "convert all InternalTaskDescriptor attributes"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ":someTask"
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getDisplayName() >> 'some task in human readable form'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task started"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.displayName == 'some task in human readable form'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TaskStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task started"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent == null
        }
    }

    def "convert to TaskSkippedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskSkippedResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getSkipMessage() >> 'SKIPPED'

        def skippedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * skippedEvent.getEventTime() >> 999
        _ * skippedEvent.getDisplayName() >> 'task skipped'
        _ * skippedEvent.getDescriptor() >> taskDescriptor
        _ * skippedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // skippedEvent always assumes a previous startEvent
        adapter.onEvent(skippedEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { TaskFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task skipped"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskSkippedResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.skipMessage == 'SKIPPED'
        }
    }

    def "convert to TaskSucceededEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskSuccessResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.isUpToDate() >> true

        def succeededEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'task succeeded'
        _ * succeededEvent.getDescriptor() >> taskDescriptor
        _ * succeededEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { TaskFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task succeeded"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.isUpToDate()
        }
    }

    def "convert to TaskFailedEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> null

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDescriptor() >> taskDescriptor

        def testResult = Mock(InternalTaskFailureResult)
        _ * testResult.getStartTime() >> 1
        _ * testResult.getEndTime() >> 2
        _ * testResult.getFailures() >> [Stub(InternalFailure)]

        def failedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'task failed'
        _ * failedEvent.getDescriptor() >> taskDescriptor
        _ * failedEvent.getResult() >> testResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as TaskFinishEvent) >> { TaskFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "task failed"
            assert event.descriptor.name == 'some task'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent == null
            assert event.result instanceof TaskFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([], [], [])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener taskListener) {
        new BuildProgressListenerAdapter([], [taskListener], [])
    }

}
