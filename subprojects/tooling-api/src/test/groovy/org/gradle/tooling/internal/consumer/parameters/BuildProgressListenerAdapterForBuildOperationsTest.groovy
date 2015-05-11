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

import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.build.BuildFailureResult
import org.gradle.tooling.events.build.BuildOperationFinishEvent
import org.gradle.tooling.events.build.BuildOperationStartEvent
import org.gradle.tooling.events.build.BuildSuccessResult
import org.gradle.tooling.events.build.internal.BuildOperationProgressListener
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.*
import spock.lang.Specification

class BuildProgressListenerAdapterForBuildOperationsTest extends Specification {

    def "adapter is only subscribing to build progress events if at least one build progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.BUILD_EXECUTION]
    }

    def "only BuildProgressEventX instances are processed if a build listener is added"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        adapter.onEvent(new Object())

        then:
        0 * listener.statusChanged(_)
    }

    def "only BuildProgressEventX instances of known type are processed"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def unknownEvent = Mock(InternalBuildProgressEvent)
        adapter.onEvent(unknownEvent)

        then:
        0 * listener.statusChanged(_)
    }

    def "conversion of start events throws exception if previous start event with same build descriptor exists"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor('id', 'some build')
        def startEvent = buildStartEvent(999, 'start', buildDesc)

        adapter.onEvent(startEvent)
        adapter.onEvent(startEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('already available')
    }

    def "conversion of non-start events throws exception if no previous start event with same build descriptor exists"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(1, 'some build')
        def finishEvent = buildFinishEvent(999, 'finish', buildDesc)

        adapter.onEvent(finishEvent)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not available')
    }

    def "looking up parent operation throws exception if no previous event for parent operation exists"() {
        given:
        final BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def childBuildDescriptor = Mock(InternalBuildDescriptor)
        _ * childBuildDescriptor.getId() >> 2
        _ * childBuildDescriptor.getName() >> 'some child'
        _ * childBuildDescriptor.getParentId() >> 1

        def childEvent = Mock(InternalBuildOperationStartedProgressEvent)
        _ * childEvent.getDisplayName() >> 'child event'
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childBuildDescriptor

        adapter.onEvent(childEvent)

        then:
        thrown(IllegalStateException)
    }

    def "conversion of child events expects parent event exists"() {
        given:
        final BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def parentTaskDescriptor = Mock(InternalTaskDescriptor)
        _ * parentTaskDescriptor.getId() >> 1
        _ * parentTaskDescriptor.getName() >> 'some parent'
        _ * parentTaskDescriptor.getParentId() >> null

        def parentEvent = Mock(InternalTaskStartedProgressEvent)
        _ * parentEvent.getEventTime() >> 999
        _ * parentEvent.getDescriptor() >> parentTaskDescriptor

        def childTaskDescriptor = Mock(InternalTaskDescriptor)
        _ * childTaskDescriptor.getId() >> 2
        _ * childTaskDescriptor.getName() >> 'some child'
        _ * childTaskDescriptor.getParentId() >> parentTaskDescriptor.getId()

        def childEvent = Mock(InternalTaskStartedProgressEvent)
        _ * childEvent.getEventTime() >> 999
        _ * childEvent.getDescriptor() >> childTaskDescriptor

        adapter.onEvent(parentEvent)
        adapter.onEvent(childEvent)

        then:
        notThrown(IllegalStateException)
    }

    def "convert all InternalBuildDescriptor attributes"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(666, 'my build', 'some build')
        def startEvent = buildStartEvent(999, 'build started', buildDesc)

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as StartEvent) >> { StartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "build started"
            assert event.descriptor.name == 'my build'
            assert event.descriptor.displayName == 'some build'
            assert event.descriptor.parent == null
        }
    }

    def "convert to BuildStartEvent"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(666, 'some build')
        def startEvent = buildStartEvent(999, 'build started', buildDesc)

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == 'build started'
            assert event.descriptor.name == 'some build'
            assert event.descriptor.parent == null
        }
    }

    def "convert to BuildSucceededEvent"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(666, 'some build')
        def startEvent = buildStartEvent(999, 'build started', buildDesc)

        def buildResult = buildSuccess(1, 2)
        def succeededEvent = buildFinishEvent(999, 'build succeeded', buildDesc, buildResult)

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == 'build succeeded'
            assert event.descriptor.name == 'some build'
            assert event.descriptor.parent == null
            assert event.result instanceof BuildSuccessResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
        }
    }

    def "convert to BuildSucceededEvent when settings evaluated"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(666, 'some build')
        def buildStart = buildStartEvent(999, 'build started', buildDesc)
        def settingsEvalStart = buildStartEvent(1000, 'settings evaluated', buildDescriptor(667, 'settings evaluated', buildDesc))
        def settingsEvalEnd = buildFinishEvent(1001, 'settings evaluated', buildDescriptor(667, 'settings evaluated', buildDesc), buildSuccess(999, 1001))

        adapter.onEvent(buildStart) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(settingsEvalStart)
        adapter.onEvent(settingsEvalEnd)

        then:
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.eventTime == 999
            assert event.displayName == 'build started'
            assert event.descriptor.name == 'some build'
            assert event.descriptor.parent == null
        }
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.eventTime == 1000
            assert event.displayName == 'settings evaluated'
            assert event.descriptor.name == 'settings evaluated'
            assert event.descriptor.parent.name == 'some build'
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.eventTime == 1001
            assert event.displayName == 'settings evaluated'
            assert event.descriptor.name == 'settings evaluated'
            assert event.descriptor.parent.name == 'some build'
            assert event.result instanceof BuildSuccessResult
            assert event.result.startTime == 999
            assert event.result.endTime == 1001
        }
    }

    def "convert to BuildFailedEvent"() {
        given:
        BuildOperationProgressListener listener = Mock(BuildOperationProgressListener)
        def adapter = createAdapter(listener)

        when:
        def buildDesc = buildDescriptor(666, 'some build')
        def startEvent = buildStartEvent(999, 'start build', buildDesc)

        def buildResult = buildFailure(1, 2, Stub(InternalFailure))
        def failedEvent = buildFinishEvent(999, 'build failed', buildDesc, buildResult)

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.eventTime == 999
            assert event.displayName == "build failed"
            assert event.descriptor.name == 'some build'
            assert event.descriptor.parent == null
            assert event.result instanceof BuildFailureResult
            assert event.result.startTime == 1
            assert event.result.endTime == 2
            assert event.result.failures.size() == 1
        }
    }

    private InternalBuildDescriptor buildDescriptor(id, String name, InternalBuildDescriptor parent = null) {
        InternalBuildDescriptor descriptor = Mock(InternalBuildDescriptor)
        descriptor.getId() >> id
        descriptor.getName() >> name
        descriptor.getParentId() >> { parent ? parent.id : null }

        descriptor
    }

    private InternalBuildDescriptor buildDescriptor(id, String name, String displayName, InternalBuildDescriptor parent = null) {
        InternalBuildDescriptor descriptor = Mock(InternalBuildDescriptor)
        descriptor.getId() >> id
        descriptor.getName() >> name
        descriptor.getDisplayName() >> displayName
        descriptor.getParentId() >> { parent ? parent.id : null }

        descriptor
    }

    private InternalBuildOperationStartedProgressEvent buildStartEvent(long eventTime, String displayName, InternalBuildDescriptor descriptor) {
        InternalBuildOperationStartedProgressEvent event = Mock(InternalBuildOperationStartedProgressEvent)
        event.getEventTime() >> eventTime
        event.getDisplayName() >> displayName
        event.getDescriptor() >> descriptor

        event
    }

    private InternalBuildOperationFinishedProgressEvent buildFinishEvent(long eventTime, String displayName, InternalBuildDescriptor descriptor, InternalBuildOperationResult result = null) {
        InternalBuildOperationFinishedProgressEvent event = Mock(InternalBuildOperationFinishedProgressEvent)
        event.getEventTime() >> eventTime
        event.getDisplayName() >> displayName
        event.getDescriptor() >> descriptor
        event.getResult() >> result

        event
    }

    private InternalBuildSuccessResult buildSuccess(long startTime, long endTime) {
        InternalBuildSuccessResult result = Mock(InternalBuildSuccessResult)
        result.startTime >> startTime
        result.endTime >> endTime

        result
    }

    private InternalBuildFailureResult buildFailure(long startTime, long endTime, InternalFailure failure) {
        InternalBuildFailureResult result = Mock(InternalBuildFailureResult)
        result.startTime >> startTime
        result.endTime >> endTime
        result.failures >> [failure]

        result
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter(new BuildProgressListenerConfiguration([], [], []))
    }

    private static BuildProgressListenerAdapter createAdapter(BuildOperationProgressListener buildListener) {
        new BuildProgressListenerAdapter(new BuildProgressListenerConfiguration([], [], [buildListener]))
    }

}
