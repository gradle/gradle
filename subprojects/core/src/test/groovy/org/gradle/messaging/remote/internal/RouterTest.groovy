/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal

import org.gradle.messaging.dispatch.DispatchFailureHandler
import org.gradle.messaging.remote.internal.protocol.RouteAvailableMessage
import org.gradle.util.ConcurrentSpecification
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.remote.internal.protocol.RouteUnavailableMessage
import org.gradle.messaging.remote.internal.protocol.RoutableMessage
import org.gradle.messaging.remote.internal.protocol.EndOfStreamEvent

class RouterTest extends ConcurrentSpecification {
    final DispatchFailureHandler<Message> handler = Mock()
    final Router router = new Router(executor, handler)

    def cleanup() {
        router?.stop()
    }

    def "forwards local route available messages to remote connection"() {
        TestRouteAvailableMessage localMessage = routeAvailable("local", [])
        Dispatch<Message> remoteReceiver = Mock()
        def forwarded = startsAsyncAction()

        given:
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()
        remote.dispatchTo(remoteReceiver)

        when:
        forwarded.started {
            local.dispatch(localMessage)
        }

        then:
        1 * remoteReceiver.dispatch(localMessage) >> { forwarded.done() }
    }

    def "forwards local route available messages to newly added remote connection"() {
        TestRouteAvailableMessage localMessage = routeAvailable("local", [])
        Dispatch<Message> remoteReceiver = Mock()
        def forwarded = startsAsyncAction()

        given:
        def local = router.createLocalConnection()
        local.dispatch(localMessage)

        when:
        forwarded.started {
            def remote = router.createRemoteConnection()
            remote.dispatchTo(remoteReceiver)
        }

        then:
        1 * remoteReceiver.dispatch(localMessage) >> { forwarded.done() }
    }

    def "forwards remote route available messages to local route"() {
        TestRouteAvailableMessage localMessage = routeAvailable("local", ["remote"])
        TestRouteAvailableMessage remoteMessage = routeAvailable("remote", [])
        Dispatch<Message> localReceiver = Mock()
        def forwarded = startsAsyncAction()

        given:
        def local = router.createLocalConnection()
        local.dispatchTo(localReceiver)
        def remote = router.createRemoteConnection()

        when:
        forwarded.started {
            local.dispatch(localMessage)
            remote.dispatch(remoteMessage)
        }

        then:
        1 * localReceiver.dispatch(remoteMessage) >> { forwarded.done() }
    }

    def "forwards remote route available message to newly added local route"() {
        TestRouteAvailableMessage localMessage = routeAvailable("local", ["remote"])
        TestRouteAvailableMessage remoteMessage = routeAvailable("remote", [])
        Dispatch<Message> localReceiver = Mock()
        def forwarded = startsAsyncAction()

        given:
        def local = router.createLocalConnection()
        local.dispatchTo(localReceiver)
        def remote = router.createRemoteConnection()

        when:
        forwarded.started {
            remote.dispatch(remoteMessage)
            local.dispatch(localMessage)
        }

        then:
        1 * localReceiver.dispatch(remoteMessage) >> { forwarded.done() }
    }

    def "does not forward remote route available messages to local route which does not accept new route"() {
        TestRouteAvailableMessage localMessage = routeAvailable("local", [])
        TestRouteAvailableMessage remoteMessage = routeAvailable("remote", [])
        Dispatch<Message> localReceiver = Mock()

        given:
        def local = router.createLocalConnection()
        local.dispatchTo(localReceiver)
        def remote = router.createRemoteConnection()

        when:
        local.dispatch(localMessage)
        remote.dispatch(remoteMessage)
        router.stop()

        then:
        0 * localReceiver._
    }

    def "forwards local route messages to remote route"() {
        Dispatch<Message> remoteOutgoing = Mock()
        def received = startsAsyncAction()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()
        def message = routeableMessage("remote")

        given:
        local.dispatch(routeAvailable("local", []))
        remote.dispatch(routeAvailable("remote", ["local"]))
        remote.dispatchTo(remoteOutgoing)

        when:
        received.started {
            local.dispatch(message)
        }

        then:
        1 * remoteOutgoing.dispatch(message) >> { received.done() }
    }

    def "broadcasts local route unavailable message to remote connections"() {
        Dispatch<Message> remoteOutgoing = Mock()
        def received = startsAsyncAction()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()
        def unavailable = routeUnavailable("local")

        local.dispatch(routeAvailable("local", []))
        remote.dispatchTo(remoteOutgoing)

        when:
        received.started {
            local.dispatch(unavailable)
        }

        then:
        1 * remoteOutgoing.dispatch({it instanceof TestRouteUnavailableMessage && it.id == 'local'}) >> { received.done() }
    }

    def "broadcasts route unavailable message on local end-of-stream"() {
        Dispatch<Message> remoteOutgoing = Mock()
        def received = startsAsyncAction()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()

        local.dispatch(routeAvailable("local", []))
        remote.dispatchTo(remoteOutgoing)

        when:
        received.started {
            local.dispatch(new EndOfStreamEvent())
        }

        then:
        1 * remoteOutgoing.dispatch({it instanceof TestRouteUnavailableMessage && it.id == 'local'}) >> { received.done() }
    }

    def "does not route messages to local connection once route unavailable received from it"() {
        Dispatch<Message> localIncoming = Mock()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()
        def available = routeAvailable("remote", [])
        def connected = startsAsyncAction()
        local.dispatchTo(localIncoming)

        when:
        connected.started {
            local.dispatch(routeAvailable("local", ["remote"]))
            remote.dispatch(available)
        }

        then:
        1 * localIncoming.dispatch(available) >> { connected.done() }

        when:
        local.dispatch(routeUnavailable("local"))
        remote.dispatch(new EndOfStreamEvent())
        router.stop()

        then:
        0 * localIncoming._
    }

    def "does not route messages to local connection once end-of-stream received from it"() {
        Dispatch<Message> localIncoming = Mock()
        def connected = startsAsyncAction()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()
        def available = routeAvailable("remote", [])
        local.dispatchTo(localIncoming)

        when:
        connected.started {
            local.dispatch(routeAvailable("local", ["remote"]))
            remote.dispatch(available)
        }

        then:
        1 * localIncoming.dispatch(available) >> { connected.done() }

        when:
        local.dispatch(new EndOfStreamEvent())
        remote.dispatch(routeUnavailable("remote"))
        router.stop()

        then:
        1 * localIncoming.dispatch(new EndOfStreamEvent())
        0 * localIncoming._
    }

    def "does not route messages to remote connection once end-of-stream received from it"() {
        Dispatch<Message> remoteIncoming = Mock()
        def local = router.createLocalConnection()
        def remote = router.createRemoteConnection()

        remote.dispatchTo(remoteIncoming)

        when:
        remote.dispatch(new EndOfStreamEvent())
        local.dispatch(routeAvailable("local", []))
        router.stop()

        then:
        1 * remoteIncoming.dispatch(new EndOfStreamEvent())
        0 * remoteIncoming._
    }

    def routeAvailable(Object id, List<Object> accepts) {
        return new TestRouteAvailableMessage(id, accepts)
    }

    def routeUnavailable(Object id) {
        return new TestRouteUnavailableMessage(id)
    }

    def routeableMessage(Object destination) {
        TestRoutableMessage message = Mock()
        _ * message.destination >> destination
        return message
    }
}

abstract class TestRoutableMessage extends Message implements RoutableMessage {
}

class TestRouteUnavailableMessage extends Message implements RouteUnavailableMessage {
    final def id

    TestRouteUnavailableMessage(id) {
        this.id = id
    }
}

class TestRouteAvailableMessage extends Message implements RouteAvailableMessage {
    final def id
    final List<Object> accepts

    TestRouteAvailableMessage(Object id, List<Object> accepts) {
        this.id = id
        this.accepts = accepts
    }

    boolean acceptIncoming(RouteAvailableMessage message) {
        return accepts.contains(message.id)
    }

    RouteUnavailableMessage getUnavailableMessage() {
        return new TestRouteUnavailableMessage(id)
    }
}