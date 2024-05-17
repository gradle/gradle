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

import spock.lang.Specification
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTaskWithExtraInfoDescriptor

import static org.junit.Assert.assertTrue

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
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "task started"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.displayName == 'some task in human readable form'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
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
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "task started"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
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
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "task skipped"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
            assertTrue event.result instanceof TaskSkippedResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            assertTrue event.result.skipMessage == 'SKIPPED'
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
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "task succeeded"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
            assertTrue event.result instanceof TaskSuccessResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            assertTrue event.result.isUpToDate()
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
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "task failed"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
            assertTrue event.result instanceof TaskFailureResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            assertTrue event.result.failures.size() == 1
        }
    }

    def "convert task dependencies"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        def dependencyTaskDescriptor = Stub(InternalTaskWithExtraInfoDescriptor)
        _ * dependencyTaskDescriptor.getId() >> ':dependency'
        _ * dependencyTaskDescriptor.getName() >> 'dependency task'
        _ * dependencyTaskDescriptor.getParentId() >> null
        _ * dependencyTaskDescriptor.getTaskPath() >> ':dependency:path'
        _ * dependencyTaskDescriptor.getDependencies() >> []

        def dependencyStartEvent = Stub(InternalOperationStartedProgressEvent)
        _ * dependencyStartEvent.getEventTime() >> 800
        _ * dependencyStartEvent.getDisplayName() >> 'task started'
        _ * dependencyStartEvent.getDescriptor() >> dependencyTaskDescriptor

        def dependencyTaskResult = Stub(InternalTaskSuccessResult)
        _ * dependencyTaskResult.getStartTime() >> 1
        _ * dependencyTaskResult.getEndTime() >> 2

        def dependencyFinishEvent = Stub(InternalOperationFinishedProgressEvent)
        _ * dependencyFinishEvent.getEventTime() >> 900
        _ * dependencyFinishEvent.getDisplayName() >> 'task finished'
        _ * dependencyFinishEvent.getDescriptor() >> dependencyTaskDescriptor
        _ * dependencyFinishEvent.getResult() >> dependencyTaskResult

        def taskDescriptor = Stub(InternalTaskWithExtraInfoDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getDependencies() >> [dependencyTaskDescriptor]
        _ * taskDescriptor.getOriginPlugin() >> null

        def startEvent = Stub(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 1000
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        when:
        adapter.onEvent(dependencyStartEvent)
        adapter.onEvent(dependencyFinishEvent)

        then:
        2 * listener.statusChanged(_)

        when:
        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assertTrue event.eventTime == 1000
            assertTrue event.displayName == "task started"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
            assertTrue event.descriptor.dependencies.size() == 1
            assertTrue event.descriptor.originPlugin == null
            with(event.descriptor.dependencies[0]) {
                assertTrue it.name == 'dependency task'
                assertTrue it.taskPath == ':dependency:path'
                assertTrue it.parent == null
                assertTrue it.dependencies.empty
            }
        }
    }

    def "convert task origin for script plugin"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        def taskOrigin = Stub(InternalScriptPluginIdentifier)
        _ * taskOrigin.getDisplayName() >> "build.gradle"
        _ * taskOrigin.getUri() >> URI.create("http://example.com/build.gradle")

        def taskDescriptor = Stub(InternalTaskWithExtraInfoDescriptor)
        _ * taskDescriptor.getParentId() >> null
        _ * taskDescriptor.getOriginPlugin() >> taskOrigin

        def startEvent = Stub(InternalOperationStartedProgressEvent)
        _ * startEvent.getDescriptor() >> taskDescriptor

        when:
        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assertTrue event.descriptor.originPlugin instanceof ScriptPluginIdentifier
            assertTrue event.descriptor.originPlugin.displayName == 'build.gradle'
            assertTrue event.descriptor.originPlugin.uri == URI.create("http://example.com/build.gradle")
        }
    }

    def "convert task origin for binary plugin"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        def taskOrigin = Stub(InternalBinaryPluginIdentifier)
        _ * taskOrigin.getDisplayName() >> "org.example"
        _ * taskOrigin.getClassName() >> "org.example.MyPlugin"
        _ * taskOrigin.getPluginId() >> "org.example"

        def taskDescriptor = Stub(InternalTaskWithExtraInfoDescriptor)
        _ * taskDescriptor.getParentId() >> null
        _ * taskDescriptor.getOriginPlugin() >> taskOrigin

        def startEvent = Stub(InternalOperationStartedProgressEvent)
        _ * startEvent.getDescriptor() >> taskDescriptor

        when:
        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assertTrue event.descriptor.originPlugin instanceof BinaryPluginIdentifier
            assertTrue event.descriptor.originPlugin.displayName == 'org.example'
            assertTrue event.descriptor.originPlugin.className == 'org.example.MyPlugin'
            assertTrue event.descriptor.originPlugin.pluginId == 'org.example'
        }
    }

    def "ignores unknown dependencies"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        def taskDescriptor = Stub(InternalTaskWithExtraInfoDescriptor)
        _ * taskDescriptor.getId() >> ':dummy'
        _ * taskDescriptor.getName() >> 'some task'
        _ * taskDescriptor.getParentId() >> null
        _ * taskDescriptor.getTaskPath() >> ':some:path'
        _ * taskDescriptor.getDependencies() >> [Stub(InternalOperationDescriptor)]

        def startEvent = Stub(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 1000
        _ * startEvent.getDisplayName() >> 'task started'
        _ * startEvent.getDescriptor() >> taskDescriptor

        when:
        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TaskStartEvent) >> { TaskStartEvent event ->
            assertTrue event.eventTime == 1000
            assertTrue event.displayName == "task started"
            assertTrue event.descriptor.name == 'some task'
            assertTrue event.descriptor.taskPath == ':some:path'
            assertTrue event.descriptor.parent == null
            assertTrue event.descriptor.dependencies.empty
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener taskListener) {
        new BuildProgressListenerAdapter([(OperationType.TASK): [taskListener]])
    }

}
