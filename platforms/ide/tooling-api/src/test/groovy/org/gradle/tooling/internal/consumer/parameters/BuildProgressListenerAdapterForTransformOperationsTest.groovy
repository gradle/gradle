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
import org.gradle.tooling.events.transform.TransformFailureResult
import org.gradle.tooling.events.transform.TransformFinishEvent
import org.gradle.tooling.events.transform.TransformStartEvent
import org.gradle.tooling.events.transform.TransformSuccessResult
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalFailureResult
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor
import spock.lang.Specification

import static org.junit.Assert.assertTrue

class BuildProgressListenerAdapterForTransformOperationsTest extends Specification {

    def "adapter is only subscribing to transform progress events if at least one transform progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.TRANSFORM_EXECUTION]
    }

    def "convert to TransformStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def transformDescriptor = Mock(InternalTransformDescriptor)
        _ * transformDescriptor.getId() >> 1
        _ * transformDescriptor.getName() >> 'Transform'
        _ * transformDescriptor.getDisplayName() >> 'Transform'
        _ * transformDescriptor.getTransformerName() >> 'SomeTransform'
        _ * transformDescriptor.getSubjectName() >> 'artifact "foo.jar"'
        _ * transformDescriptor.getDependencies() >> []

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Transform started'
        _ * startEvent.getDescriptor() >> transformDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TransformStartEvent) >> { TransformStartEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Transform started"
            assertTrue event.descriptor.displayName == 'Transform'
            assertTrue event.descriptor.transformer.displayName == 'SomeTransform'
            assertTrue event.descriptor.subject.displayName == 'artifact "foo.jar"'
        }
    }

    def "convert to TransformSuccessResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def transformDescriptor = Mock(InternalTransformDescriptor)
        _ * transformDescriptor.getId() >> 1
        _ * transformDescriptor.getName() >> 'Transform'
        _ * transformDescriptor.getDisplayName() >> 'Transform'
        _ * transformDescriptor.getTransformerName() >> 'SomeTransform'
        _ * transformDescriptor.getSubjectName() >> 'artifact "foo.jar"'
        _ * transformDescriptor.getDependencies() >> []

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Transform started'
        _ * startEvent.getDescriptor() >> transformDescriptor

        def transformResult = Mock(InternalSuccessResult)
        _ * transformResult.getStartTime() >> 1
        _ * transformResult.getEndTime() >> 2

        def succeededEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'Transform succeeded'
        _ * succeededEvent.getDescriptor() >> transformDescriptor
        _ * succeededEvent.getResult() >> transformResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as TransformFinishEvent) >> { TransformFinishEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Transform succeeded"
            assertTrue event.descriptor.displayName == 'Transform'
            assertTrue event.descriptor.transformer.displayName == 'SomeTransform'
            assertTrue event.descriptor.subject.displayName == 'artifact "foo.jar"'
            assertTrue event.descriptor.parent == null
            assertTrue event.result instanceof TransformSuccessResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
        }
    }

    def "convert to TransformFailureResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        when:
        def transformDescriptor = Mock(InternalTransformDescriptor)
        _ * transformDescriptor.getId() >> 1
        _ * transformDescriptor.getName() >> 'Transform'
        _ * transformDescriptor.getDisplayName() >> 'Transform'
        _ * transformDescriptor.getTransformerName() >> 'SomeTransform'
        _ * transformDescriptor.getSubjectName() >> 'artifact "foo.jar"'
        _ * transformDescriptor.getDependencies() >> []

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Transform started'
        _ * startEvent.getDescriptor() >> transformDescriptor

        def transformResult = Mock(InternalFailureResult)
        _ * transformResult.getStartTime() >> 1
        _ * transformResult.getEndTime() >> 2
        _ * transformResult.getFailures() >> [Stub(InternalFailure)]

        def failedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'Transform failed'
        _ * failedEvent.getDescriptor() >> transformDescriptor
        _ * failedEvent.getResult() >> transformResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as TransformFinishEvent) >> { TransformFinishEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Transform failed"
            assertTrue event.descriptor.displayName == 'Transform'
            assertTrue event.descriptor.transformer.displayName == 'SomeTransform'
            assertTrue event.descriptor.subject.displayName == 'artifact "foo.jar"'
            assertTrue event.result instanceof TransformFailureResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            assertTrue event.result.failures.size() == 1
        }
    }

    def "convert transform dependencies"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)

        def dependencyTransformDescriptor = Stub(InternalTransformDescriptor)
        _ * dependencyTransformDescriptor.getId() >> ':dependency'
        _ * dependencyTransformDescriptor.getName() >> 'dependency transform'
        _ * dependencyTransformDescriptor.getParentId() >> null
        _ * dependencyTransformDescriptor.getDependencies() >> []

        def dependencyStartEvent = Stub(InternalOperationStartedProgressEvent)
        _ * dependencyStartEvent.getEventTime() >> 800
        _ * dependencyStartEvent.getDisplayName() >> 'transform started'
        _ * dependencyStartEvent.getDescriptor() >> dependencyTransformDescriptor

        def dependencyTransformResult = Stub(InternalSuccessResult)
        _ * dependencyTransformResult.getStartTime() >> 1
        _ * dependencyTransformResult.getEndTime() >> 2

        def dependencyFinishEvent = Stub(InternalOperationFinishedProgressEvent)
        _ * dependencyFinishEvent.getEventTime() >> 900
        _ * dependencyFinishEvent.getDisplayName() >> 'transform finished'
        _ * dependencyFinishEvent.getDescriptor() >> dependencyTransformDescriptor
        _ * dependencyFinishEvent.getResult() >> dependencyTransformResult

        def transformDescriptor = Stub(InternalTransformDescriptor)
        _ * transformDescriptor.getId() >> ':dummy'
        _ * transformDescriptor.getName() >> 'some transform'
        _ * transformDescriptor.getParentId() >> null
        _ * transformDescriptor.getDependencies() >> [dependencyTransformDescriptor]

        def startEvent = Stub(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 1000
        _ * startEvent.getDisplayName() >> 'transform started'
        _ * startEvent.getDescriptor() >> transformDescriptor

        when:
        adapter.onEvent(dependencyStartEvent)
        adapter.onEvent(dependencyFinishEvent)

        then:
        2 * listener.statusChanged(_)

        when:
        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as TransformStartEvent) >> { TransformStartEvent event ->
            assertTrue event.eventTime == 1000
            assertTrue event.displayName == "transform started"
            assertTrue event.descriptor.name == 'some transform'
            assertTrue event.descriptor.parent == null
            assertTrue event.descriptor.dependencies.size() == 1
            with(event.descriptor.dependencies[0]) {
                assertTrue it.name == 'dependency transform'
                assertTrue it.parent == null
                assertTrue it.dependencies.empty
            }
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener listener) {
        new BuildProgressListenerAdapter([(OperationType.TRANSFORM): [listener]])
    }

}
