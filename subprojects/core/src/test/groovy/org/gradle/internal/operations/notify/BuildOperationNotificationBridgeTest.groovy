/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.notify

import org.gradle.api.internal.GradleInternal
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.recorder.BuildOperationRecorder
import org.gradle.internal.progress.BuildOperationCategory
import org.gradle.internal.progress.BuildOperationDescriptor
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationListenerManager
import org.gradle.internal.progress.DefaultBuildOperationListenerManager
import org.gradle.internal.progress.OperationFinishEvent
import org.gradle.internal.progress.OperationStartEvent
import org.gradle.testing.internal.util.Specification

class BuildOperationNotificationBridgeTest extends Specification {

    def rawListenerManager = new DefaultListenerManager()
    def listenerManager = new DefaultBuildOperationListenerManager(rawListenerManager)
    BuildOperationNotificationBridge bridge
    def broadcast = rawListenerManager.getBroadcaster(BuildOperationListener)
    def listener = Mock(BuildOperationNotificationListener)
    def recorder = Mock(BuildOperationRecorder)
    def gradle = Mock(GradleInternal)

    def "removes listener when stopped"() {
        given:
        listenerManager = Mock(BuildOperationListenerManager)
        def buildOperationListener

        when:
        register(listener)

        then:
        1 * listenerManager.addListener(_) >> {
            buildOperationListener = it[0]
        }

        when:
        bridge.stop()

        then:
        1 * listenerManager.removeListener(_) >> {
            assert buildOperationListener == it[0]
        }
    }

    def "does not allow duplicate registration"() {
        when:
        register(listener)
        register(listener)

        then:
        thrown IllegalStateException
    }

    def "passes recorded events to listeners registering"() {
        when:
        register(listener, [recordedBuildOperation(new OperationStartEvent(0)),
                            recordedBuildOperation(new OperationFinishEvent(1, 2, null, null))])

        then:
        1 * listener.started(_)
        1 * listener.finished(_)
        1 * recorder.stop()
    }

    def "forwards operations with details"() {
        given:
        def d1 = d(1, null, 1)
        def d2 = d(2, null, null)
        def d3 = d(3, null, 3)
        def e1 = new Exception()
        register(listener)

        // operation with details and non null result
        when:
        broadcast.started(d1, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == 1
            assert n.notificationOperationDetails.is(d1.details)
        }

        when:
        broadcast.finished(d1, new OperationFinishEvent(-1, -1, null, 10))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d1.id
            assert n.notificationOperationResult == 10
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d1.details)
        }

        // operation with no details
        when:
        broadcast.started(d2, null)

        then:
        0 * listener.started(_)

        when:
        broadcast.finished(d2, new OperationFinishEvent(-1, -1, null, 10))

        then:
        0 * listener.finished(_)

        // operation with details and null result
        when:
        broadcast.started(d3, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
        }

        when:
        broadcast.finished(d3, new OperationFinishEvent(-1, -1, null, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d3.details)
        }

        // operation with details and failure
        when:
        broadcast.started(d3, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
        }

        when:
        broadcast.finished(d3, new OperationFinishEvent(-1, -1, e1, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == e1
            assert n.notificationOperationDetails.is(d3.details)
        }
    }

    BuildOperationDescriptor d(Long id, Long parentId, Long details) {
        BuildOperationDescriptor.displayName(id.toString()).details(details).build(id, parentId)
    }

    def "parentId is of last parent that a notification was sent for"() {
        given:
        register(listener)
        def d1 = d(1, null, 1)
        def d2 = d(2, 1, null)
        def d3 = d(3, 2, 3)
        def d4 = d(4, 2, 4)
        def d5 = d(5, 4, 5)
        def d6 = d(6, 5, null)
        def d7 = d(7, 6, 7)

        when:
        broadcast.started(d1, null)
        broadcast.started(d2, null)

        broadcast.started(d3, null)
        broadcast.finished(d3, new OperationFinishEvent(-1, -1, null, null))

        broadcast.started(d4, null)
        broadcast.started(d5, null)
        broadcast.started(d6, null)
        broadcast.started(d7, null)

        broadcast.finished(d7, new OperationFinishEvent(-1, -1, null, null))
        broadcast.finished(d6, new OperationFinishEvent(-1, -1, null, null))
        broadcast.finished(d5, new OperationFinishEvent(-1, -1, null, null))
        broadcast.finished(d4, new OperationFinishEvent(-1, -1, null, null))

        broadcast.finished(d2, new OperationFinishEvent(-1, -1, null, null))
        broadcast.finished(d1, new OperationFinishEvent(-1, -1, null, null))

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d1.id
        }

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationParentId == d1.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
        }

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d4.id
            assert n.notificationOperationParentId == d1.id
        }

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d5.id
            assert n.notificationOperationParentId == d4.id
        }

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d7.id
            assert n.notificationOperationParentId == d5.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d7.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d5.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d4.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d1.id
        }

    }

    BuildOperationRecorder.RecordedBuildOperation recordedBuildOperation(Object buildOperationEvent) {
        def operationDescriptor = new BuildOperationDescriptor(1,
            null,
            "name",
            "displayName",
            "progress",
            "Details",
            BuildOperationCategory.UNCATEGORIZED)
        return new BuildOperationRecorder.RecordedBuildOperation(operationDescriptor, buildOperationEvent)
    }

    void register(BuildOperationNotificationListener listener, List<BuildOperationRecorder.RecordedBuildOperation> recordedBuildOps = []) {
        _ * recorder.getRecordedEvents() >> recordedBuildOps
        if (bridge == null) {
            bridge = new BuildOperationNotificationBridge(listenerManager, recorder, gradle)
        }
        bridge.registerBuildScopeListener(listener)
    }
}
