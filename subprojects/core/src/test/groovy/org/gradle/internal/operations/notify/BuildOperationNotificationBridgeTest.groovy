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


import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.DefaultBuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.time.TestTime
import spock.lang.Specification

import javax.annotation.Nullable

class BuildOperationNotificationBridgeTest extends Specification {

    def listenerManager = new DefaultListenerManager(Scope.BuildSession)
    def buildOperationListenerManager = new DefaultBuildOperationListenerManager()
    def broadcast = buildOperationListenerManager.broadcaster
    def listener = Mock(BuildOperationNotificationListener)

    BuildOperationNotificationBridge bridgeInstance

    def "removes listener when stopped"() {
        given:
        def buildOperationListener
        buildOperationListenerManager = Mock(BuildOperationListenerManager)

        when:
        def bridge = getOrCreateBridge()
        bridge.valve.start()

        then:
        1 * buildOperationListenerManager.addListener(_) >> {
            buildOperationListener = it[0]
        }

        when:
        bridge.valve.stop()

        then:
        1 * buildOperationListenerManager.removeListener(_) >> {
            assert buildOperationListener == it[0]
        }
    }

    def "does not allow duplicate registration"() {
        when:
        def bridge = getOrCreateBridge()
        bridge.valve.start()
        bridge.register(listener)
        bridge.register(listener)

        then:
        thrown IllegalStateException
    }

    def "can register again after resetting valve"() {
        when:
        def bridge = getOrCreateBridge()
        bridge.valve.start()
        bridge.register(listener)
        bridge.valve.stop()
        bridge.valve.start()
        bridge.register(listener)

        then:
        noExceptionThrown()
    }

    def "cannot register when valve is closed"() {
        when:
        register(listener)

        then:
        thrown IllegalStateException
    }

    def "passes recorded events to listeners registering"() {
        def d1 = d(1, null, 1)
        def bridge = getOrCreateBridge()
        bridge.valve.start()

        when:
        broadcast.started(d1, startEvent(0))
        broadcast.finished(d1, finishEvent(0, 1, null, ""))

        and:
        bridge.register(listener)

        then:
        1 * listener.started(_)
        1 * listener.finished(_)
    }

    def "forwards operations with details"() {
        given:
        def d1 = d(1, null, 1)
        def d2 = d(2, null, null)
        def d3 = d(3, null, 3)
        def e1 = new Exception()
        getOrCreateBridge().valve.start()
        register(listener)

        // operation with details and non null result
        when:
        broadcast.started(d1, startEvent(0))

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == new OperationIdentifier(1)
            assert n.notificationOperationDetails.is(d1.details)
            assert n.notificationOperationStartedTimestamp == 0
        }

        when:
        broadcast.finished(d1, finishEvent(0, 10, null, 10))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d1.id
            assert n.notificationOperationResult == 10
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d1.details)
            assert n.notificationOperationFinishedTimestamp == 10
        }

        // operation with no details
        when:
        broadcast.started(d2, startEvent(20))

        then:
        0 * listener.started(_)

        when:
        broadcast.finished(d2, finishEvent(20, 30, null, 10))

        then:
        0 * listener.finished(_)

        // operation with details and null result
        when:
        broadcast.started(d3, startEvent(40))

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
            assert n.notificationOperationStartedTimestamp == 40
        }

        when:
        broadcast.finished(d3, finishEvent(40, 50, null, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d3.details)
            assert n.notificationOperationFinishedTimestamp == 50
        }

        // operation with details and failure
        when:
        broadcast.started(d3, startEvent(60))

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
        }

        when:
        broadcast.finished(d3, finishEvent(60, 70, e1, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == e1
            assert n.notificationOperationDetails.is(d3.details)
        }
    }

    BuildOperationDescriptor d(Long id, Long parentId, Long details) {
        BuildOperationDescriptor.displayName(id.toString()).details(details).build(
            new OperationIdentifier(id),
            parentId == null ? null : new OperationIdentifier(parentId)
        )
    }

    def "parentId is of last parent that a notification was sent for"() {
        given:
        getOrCreateBridge().valve.start()
        register(listener)
        def d1 = d(1, null, 1)
        def d2 = d(2, 1, null)
        def d3 = d(3, 2, 3)
        def d4 = d(4, 2, 4)
        def d5 = d(5, 4, 5)
        def d6 = d(6, 5, null)
        def d7 = d(7, 6, 7)

        when:
        broadcast.started(d1, startEvent(0))
        broadcast.started(d2, null)

        broadcast.started(d3, startEvent(0))
        broadcast.finished(d3, finishEvent(-1, -1, null, null))

        broadcast.started(d4, startEvent(0))
        broadcast.started(d5, startEvent(0))
        broadcast.started(d6, startEvent(0))
        broadcast.started(d7, startEvent(0))

        broadcast.finished(d7, finishEvent(-1, -1, null, null))
        broadcast.finished(d6, finishEvent(-1, -1, null, null))
        broadcast.finished(d5, finishEvent(-1, -1, null, null))
        broadcast.finished(d4, finishEvent(-1, -1, null, null))

        broadcast.finished(d2, finishEvent(-1, -1, null, null))
        broadcast.finished(d1, finishEvent(-1, -1, null, null))

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

    def "emits progress events"() {
        given:
        getOrCreateBridge().valve.start()
        register(listener)
        def d1 = d(1, null, 1)
        def d2 = d(2, 1, null)
        def d3 = d(3, 2, 3)

        when:
        broadcast.started(d1, startEvent(0))
        broadcast.progress(d1.id, progressEvent(0, 1))
        broadcast.progress(d1.id, progressEvent(0, null))

        broadcast.started(d2, null)
        broadcast.progress(d2.id, progressEvent(0, 2))

        broadcast.started(d3, startEvent(0))
        broadcast.progress(d3.id, progressEvent(0, 1))
        broadcast.finished(d3, finishEvent(-1, -1, null, null))


        broadcast.finished(d2, finishEvent(-1, -1, null, null))
        broadcast.finished(d1, finishEvent(-1, -1, null, null))

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d1.id
        }

        then:
        1 * listener.progress(_) >> { BuildOperationProgressNotification n ->
            assert n.notificationOperationId == d1.id
            assert n.notificationOperationProgressDetails == 1
        }

        then:
        1 * listener.progress(_) >> { BuildOperationProgressNotification n ->
            assert n.notificationOperationId == d1.id
            assert n.notificationOperationProgressDetails == 2
        }

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationParentId == d1.id
        }

        then:
        1 * listener.progress(_) >> { BuildOperationProgressNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationProgressDetails == 1
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
        }

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d1.id
        }
    }

    void register(BuildOperationNotificationListener listener) {
        getOrCreateBridge().register(listener)
    }

    BuildOperationNotificationBridge getOrCreateBridge() {
        if (bridgeInstance == null) {
            bridgeInstance = new BuildOperationNotificationBridge(buildOperationListenerManager, listenerManager)
        } else {
            bridgeInstance
        }
    }

    private OperationStartEvent startEvent(long startTime) {
        return new OperationStartEvent(TestTime.timestampOf(startTime))
    }

    private OperationFinishEvent finishEvent(long startTime, long endTime, @Nullable Throwable failure, @Nullable Object result) {
        return new OperationFinishEvent(TestTime.timestampOf(startTime), TestTime.timestampOf(endTime), failure, result)
    }

    private OperationProgressEvent progressEvent(long time, Object details) {
        return new OperationProgressEvent(TestTime.timestampOf(time), details)
    }
}
