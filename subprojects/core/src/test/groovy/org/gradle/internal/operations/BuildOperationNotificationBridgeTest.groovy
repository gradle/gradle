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

package org.gradle.internal.operations

import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.internal.progress.BuildOperationDescriptor
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationListenerManager
import org.gradle.internal.progress.OperationFinishEvent
import org.gradle.testing.internal.util.Specification

class BuildOperationNotificationBridgeTest extends Specification {

    def buildOperationService = Mock(BuildOperationListenerManager)
    def bridge = new BuildOperationNotificationBridge(buildOperationService)
    def listener = Mock(BuildOperationNotificationListener)

    def "adapts listener"() {
        when:
        register(listener)

        then:
        1 * buildOperationService.addListener(_)
    }

    def "removes listener when stopped"() {
        given:
        def buildOperationListener

        when:
        register(listener)

        then:
        1 * buildOperationService.addListener(_) >> {
            buildOperationListener = it[0]
        }

        when:
        bridge.stop()

        then:
        1 * buildOperationService.removeListener(_) >> {
            assert buildOperationListener == it[0]
        }
    }

    def "forwards operations with details"() {
        given:
        BuildOperationListener buildOperationListener
        def d1 = BuildOperationDescriptor.displayName("a").details(1).build(1, null)
        def d2 = BuildOperationDescriptor.displayName("a").build(2, null)
        def d3 = BuildOperationDescriptor.displayName("a").details(1).build(3, null)
        def e1 = new Exception()

        when:
        register(listener)

        then:
        1 * buildOperationService.addListener(_) >> {
            buildOperationListener = it[0]
        }

        // operation with details and non null result
        when:
        buildOperationListener.started(d1, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == 1
            assert n.notificationOperationDetails.is(d1.details)
        }

        when:
        buildOperationListener.finished(d1, new OperationFinishEvent(-1, -1, null, 10))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d1.id
            assert n.notificationOperationResult == 10
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d1.details)
        }

        // operation with no details
        when:
        buildOperationListener.started(d2, null)

        then:
        0 * listener.started(_)

        when:
        buildOperationListener.finished(d2, new OperationFinishEvent(-1, -1, null, 10))

        then:
        0 * listener.finished(_)

        // operation with details and null result
        when:
        buildOperationListener.started(d3, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
        }

        when:
        buildOperationListener.finished(d3, new OperationFinishEvent(-1, -1, null, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == null
            assert n.notificationOperationDetails.is(d3.details)
        }

        // operation with details and failure
        when:
        buildOperationListener.started(d3, null)

        then:
        1 * listener.started(_) >> { BuildOperationStartedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationDetails.is(d3.details)
        }

        when:
        buildOperationListener.finished(d3, new OperationFinishEvent(-1, -1, e1, null))

        then:
        1 * listener.finished(_) >> { BuildOperationFinishedNotification n ->
            assert n.notificationOperationId == d3.id
            assert n.notificationOperationResult == null
            assert n.notificationOperationFailure == e1
            assert n.notificationOperationDetails.is(d3.details)
        }
    }

    void register(BuildOperationNotificationListener listener) {
        bridge.notificationListenerRegistrar().registerBuildScopeListener(listener)
    }
}
