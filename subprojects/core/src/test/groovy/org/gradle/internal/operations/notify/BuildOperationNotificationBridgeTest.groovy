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
import org.gradle.internal.operations.*
import org.gradle.testing.internal.util.Specification

class BuildOperationNotificationBridgeTest extends Specification {

    def listenerManager = new DefaultListenerManager()
    def buildOperationListenerManager = new DefaultBuildOperationListenerManager()
    def broadcast = buildOperationListenerManager.broadcaster
    def listener = Mock(BuildOperationNotificationListener)
    def gradle = Mock(GradleInternal)

    BuildOperationNotificationBridge bridgeInstance

    def "removes listener when stopped"() {
        given:
        def buildOperationListener
        buildOperationListenerManager = Mock(BuildOperationListenerManager)

        when:
        def bridge = bridge()
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

    BuildOperationDescriptor d(Long id, Long parentId, Long details) {
        BuildOperationDescriptor.displayName(id.toString()).details(details).build(
            new OperationIdentifier(id),
            parentId == null ? null : new OperationIdentifier(parentId)
        )
    }

    def "emits progress events"() {
        given:
        bridge().valve.start()
        register(listener)
        def d1 = d(1, null, 1)
        def d2 = d(2, 1, null)
        def d3 = d(3, 2, 3)

        when:
        broadcast.started(d1, new OperationStartEvent(0))
        broadcast.progress(d1.id, new OperationProgressEvent(0, 1))
        broadcast.progress(d1.id, new OperationProgressEvent(0, null))

        broadcast.started(d2, null)
        broadcast.progress(d2.id, new OperationProgressEvent(0, 2))

        broadcast.started(d3, new OperationStartEvent(0))
        broadcast.progress(d3.id, new OperationProgressEvent(0, 1))
        broadcast.finished(d3, new OperationFinishEvent(-1, -1, null, null))


        broadcast.finished(d2, new OperationFinishEvent(-1, -1, null, null))
        broadcast.finished(d1, new OperationFinishEvent(-1, -1, null, null))

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
        bridge().registrar.register(listener)
    }

    BuildOperationNotificationBridge bridge() {
        if (bridgeInstance == null) {
            bridgeInstance = new BuildOperationNotificationBridge(buildOperationListenerManager, listenerManager)
        } else {
            bridgeInstance
        }
    }
}
