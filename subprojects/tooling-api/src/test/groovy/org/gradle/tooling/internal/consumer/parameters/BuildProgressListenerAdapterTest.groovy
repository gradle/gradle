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
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import spock.lang.Specification

class BuildProgressListenerAdapterTest extends Specification {

    def "adapter can subscribe to multiple progress events"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when: 'we register a new test listener'
        adapter = createAdapter(Mock(ProgressListener), null, null)

        then: 'test execution becomes a subscribed operation'
        adapter.subscribedOperations as Set == [InternalBuildProgressListener.TEST_EXECUTION] as Set

        when: 'we register a new task listener'
        adapter = createAdapter(Mock(ProgressListener), Mock(ProgressListener), null)

        then: 'task execution becomes a subscribed operation'
        adapter.subscribedOperations as Set == [InternalBuildProgressListener.TEST_EXECUTION, InternalBuildProgressListener.TASK_EXECUTION] as Set

        when: 'we register a new build listener'
        adapter = createAdapter(Mock(ProgressListener), Mock(ProgressListener), Mock(ProgressListener))

        then: 'build execution becomes a subscribed operation'
        adapter.subscribedOperations as Set == [InternalBuildProgressListener.TEST_EXECUTION, InternalBuildProgressListener.TASK_EXECUTION, InternalBuildProgressListener.BUILD_EXECUTION] as Set
    }

    def "parent descriptor of a descriptor can be of a different type"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(null, listener, null)

        when:
        def buildDescriptor = Mock(InternalOperationDescriptor)
        _ * buildDescriptor.getId() >> 1
        _ * buildDescriptor.getName() >> 'my build'
        _ * buildDescriptor.getParentId() >> null

        def taskDescriptor = Mock(InternalTaskDescriptor)
        _ * taskDescriptor.getId() >> 2
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getParentId() >> buildDescriptor.getId()

        def buildStartEvent = Mock(InternalOperationStartedProgressEvent)
        _ * buildStartEvent.getEventTime() >> 999
        _ * buildStartEvent.getDisplayName() >> 'build started'
        _ * buildStartEvent.getDescriptor() >> buildDescriptor

        def taskStartEvent = Mock(InternalOperationStartedProgressEvent)
        _ * taskStartEvent.getEventTime() >> 1001
        _ * taskStartEvent.getDisplayName() >> 'task started'
        _ * taskStartEvent.getDescriptor() >> taskDescriptor

        adapter.onEvent(buildStartEvent)
        adapter.onEvent(taskStartEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assert event.eventTime == 1001
            assert event.displayName == 'task started'
            assert event.descriptor.name == 'some task'
            assert event.descriptor.taskPath == ':some:path'
            assert event.descriptor.parent.name == 'my build'
            assert event.descriptor.parent.parent == null
        }
    }

    BuildProgressListenerAdapter createAdapter() {
        createAdapter(null, null, null)
    }

    BuildProgressListenerAdapter createAdapter(ProgressListener testListener) {
        createAdapter(testListener, null, null)
    }

    BuildProgressListenerAdapter createAdapter(ProgressListener testListener, ProgressListener taskListener, ProgressListener buildListener) {
        new BuildProgressListenerAdapter(testListener ? [testListener] : [], taskListener ? [taskListener] : [], buildListener ? [buildListener] : [])
    }

}
