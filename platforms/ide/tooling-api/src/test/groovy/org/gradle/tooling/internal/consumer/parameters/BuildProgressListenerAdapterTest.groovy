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

import com.google.common.collect.Sets
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import spock.lang.Specification

import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.BUILD_EXECUTION
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.BUILD_PHASE
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.FILE_DOWNLOAD
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.PROBLEMS
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.TASK_EXECUTION
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.TEST_EXECUTION
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.TEST_OUTPUT
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.TRANSFORM_EXECUTION
import static org.gradle.tooling.internal.protocol.InternalBuildProgressListener.WORK_ITEM_EXECUTION

class BuildProgressListenerAdapterTest extends Specification {

    def "adapter can subscribe to progress events of types #operationTypes"() {
        given:
        Map<OperationType, List<ProgressListener>> listeners = operationTypes.collectEntries {
            [(it): [Stub(ProgressListener)]]
        }

        when:
        def adapter = new BuildProgressListenerAdapter(listeners)

        then:
        adapter.subscribedOperations as Set == expectedSubscribedOperations

        where:
        operationTypes << Sets.powerSet(EnumSet.allOf(OperationType))
        expectedSubscribedOperations << Sets.powerSet([TEST_EXECUTION, TASK_EXECUTION,
                                                       BUILD_EXECUTION, WORK_ITEM_EXECUTION,
                                                       PROJECT_CONFIGURATION_EXECUTION, TRANSFORM_EXECUTION,
                                                       TEST_OUTPUT, FILE_DOWNLOAD, BUILD_PHASE, PROBLEMS] as Set)
    }

    def "parent descriptor of a descriptor can be of a different type"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = new BuildProgressListenerAdapter([(OperationType.TASK): [listener]])

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


}
