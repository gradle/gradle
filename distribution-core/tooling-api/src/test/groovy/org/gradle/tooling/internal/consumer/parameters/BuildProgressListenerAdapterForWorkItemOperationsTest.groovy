/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.tooling.events.work.WorkItemFailureResult
import org.gradle.tooling.events.work.WorkItemFinishEvent
import org.gradle.tooling.events.work.WorkItemStartEvent
import org.gradle.tooling.events.work.WorkItemSuccessResult
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalFailureResult
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalWorkItemDescriptor
import spock.lang.Specification

class BuildProgressListenerAdapterForWorkItemOperationsTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.WORK_ITEM_EXECUTION]
    }

    def "convert to WorkItemStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def workItemDescriptor = Mock(InternalWorkItemDescriptor)
        _ * workItemDescriptor.getId() >> 1
        _ * workItemDescriptor.getName() >> 'Test Work'
        _ * workItemDescriptor.getDisplayName() >> 'Test Work'
        _ * workItemDescriptor.getClassName() >> 'SomeRunnable'

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Test Work started'
        _ * startEvent.getDescriptor() >> workItemDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as WorkItemStartEvent) >> { WorkItemStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "Test Work started"
            assert event.descriptor.displayName == 'Test Work'
            assert event.descriptor.className == 'SomeRunnable'
        }
    }

    def "convert to WorkItemSuccessResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def workItemDescriptor = Mock(InternalWorkItemDescriptor)
        _ * workItemDescriptor.getId() >> 1
        _ * workItemDescriptor.getName() >> 'Test Work'
        _ * workItemDescriptor.getDisplayName() >> 'Test Work'
        _ * workItemDescriptor.getClassName() >> 'SomeRunnable'

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Test Work started'
        _ * startEvent.getDescriptor() >> workItemDescriptor

        def workItemResult = Mock(InternalSuccessResult)
        _ * workItemResult.getStartTime() >> 1
        _ * workItemResult.getEndTime() >> 2

        def succeededEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'Test Work succeeded'
        _ * succeededEvent.getDescriptor() >> workItemDescriptor
        _ * succeededEvent.getResult() >> workItemResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as WorkItemFinishEvent) >> { WorkItemFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "Test Work succeeded"
            assert event.descriptor.displayName == 'Test Work'
            assert event.descriptor.className == 'SomeRunnable'
            assert event.descriptor.parent == null
            assert event.result instanceof WorkItemSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to WorkItemFailureResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def workItemDescriptor = Mock(InternalWorkItemDescriptor)
        _ * workItemDescriptor.getId() >> 1
        _ * workItemDescriptor.getName() >> 'Test Work'
        _ * workItemDescriptor.getDisplayName() >> 'Test Work'
        _ * workItemDescriptor.getClassName() >> 'SomeRunnable'

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Test Work started'
        _ * startEvent.getDescriptor() >> workItemDescriptor

        def workItemResult = Mock(InternalFailureResult)
        _ * workItemResult.getStartTime() >> 1
        _ * workItemResult.getEndTime() >> 2
        _ * workItemResult.getFailures() >> [Stub(InternalFailure)]

        def failedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'Test Work failed'
        _ * failedEvent.getDescriptor() >> workItemDescriptor
        _ * failedEvent.getResult() >> workItemResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as WorkItemFinishEvent) >> { WorkItemFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "Test Work failed"
            assert event.descriptor.displayName == 'Test Work'
            assert event.descriptor.className == 'SomeRunnable'
            assert event.result instanceof WorkItemFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener testListener) {
        new BuildProgressListenerAdapter([(OperationType.WORK_ITEM): [testListener]])
    }

}
