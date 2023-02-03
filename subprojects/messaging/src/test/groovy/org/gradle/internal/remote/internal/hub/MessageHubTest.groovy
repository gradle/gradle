/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal.hub

import org.gradle.api.Action
import org.gradle.internal.dispatch.BoundedDispatch
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.TestConnection
import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue

@Timeout(60)
class MessageHubTest extends ConcurrentSpec {
    final Action<Throwable> errorHandler = Mock()
    final MessageHub hub = new MessageHub("<hub>", executorFactory, errorHandler)

    def cleanup() {
        hub.stop()
    }

    def "creates a separate dispatcher for each outgoing type"() {
        when:
        def dispatcher1 = hub.getOutgoing("channel1", String)
        def dispatcher2 = hub.getOutgoing("channel1", Integer)

        then:
        dispatcher1 != null
        dispatcher2 != null
        dispatcher1 != dispatcher2
    }

    def "creates a separate dispatcher for each outgoing channel"() {
        when:
        def dispatcher1 = hub.getOutgoing("channel1", String)
        def dispatcher2 = hub.getOutgoing("channel2", String)

        then:
        dispatcher1 != null
        dispatcher2 != null
        dispatcher1 != dispatcher2
    }

    def "can dispatch outgoing messages"() {
        given:
        def dispatcher = hub.getOutgoing("channel", String)

        expect:
        dispatcher.dispatch("message 1")
        dispatcher.dispatch("message 2")
    }

    def "outgoing messages are dispatched asynchronously to connection"() {
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection = new MockOutgoingConnection(outgoing)

        given:
        hub.addConnection(connection)

        when:
        operation.dispatch {
            hub.getOutgoing("channel1", String).dispatch("message1")
            hub.getOutgoing("channel1", String).dispatch("message2")
            hub.getOutgoing("channel2", Long).dispatch(12)
        }
        thread.blockUntil.longDispatched

        then:
        1 * outgoing.dispatch({ it.payload == "message1" }) >> {
            thread.blockUntil.dispatch
            instant.message1Dispatched
        }
        1 * outgoing.dispatch({ it.payload == "message2" }) >> {
            instant.message2Dispatched
        }
        1 * outgoing.dispatch({ it.payload == 12 }) >> {
            instant.longDispatched
        }
        _ * outgoing.flush()
        0 * _._

        and:
        operation.dispatch.end < instant.message1Dispatched
        instant.message1Dispatched < instant.message2Dispatched
        instant.message2Dispatched < instant.longDispatched

        cleanup:
        connection.stop()
    }

    def "queued outgoing messages are dispatched asynchronously to connection when connection is added"() {
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection = new MockOutgoingConnection(outgoing)

        given:
        hub.getOutgoing("channel1", String).dispatch("message1")
        hub.getOutgoing("channel1", String).dispatch("message2")
        hub.getOutgoing("channel2", Long).dispatch(12)

        when:
        operation.connection {
            hub.addConnection(connection)
        }
        thread.blockUntil.longDispatched

        then:
        1 * outgoing.dispatch({ it.payload == "message1" }) >> {
            thread.blockUntil.connection
            instant.message1Dispatched
        }
        1 * outgoing.dispatch({ it.payload == "message2" }) >> {
            instant.message2Dispatched
        }
        1 * outgoing.dispatch({ it.payload == 12 }) >> {
            instant.longDispatched
        }
        _ * outgoing.flush()
        0 * _._

        and:
        operation.connection.end < instant.message1Dispatched
        instant.message1Dispatched < instant.message2Dispatched
        instant.message2Dispatched < instant.longDispatched

        cleanup:
        connection.stop()
    }

    def "stop blocks until all outgoing messages dispatched to connection"() {
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection = new MockOutgoingConnection(outgoing)

        when:
        hub.addConnection(connection)
        hub.getOutgoing("channel1", String).dispatch("message1")
        hub.getOutgoing("channel1", String).dispatch("message2")
        hub.getOutgoing("channel2", Long).dispatch(12)
        hub.stop()

        then:
        1 * outgoing.dispatch({ it instanceof ChannelMessage && it.payload == "message1" })
        1 * outgoing.dispatch({ it instanceof ChannelMessage && it.payload == "message2" })
        1 * outgoing.dispatch({ it instanceof ChannelMessage && it.payload == 12 })
        1 * outgoing.dispatch({ it instanceof EndOfStream}) >> { connection.stop() }
        (1.._) * outgoing.flush()
        0 * _._
    }

    def "each outgoing message is dispatched in order to connection"() {
        def messages = new CopyOnWriteArrayList()
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection = new MockOutgoingConnection(outgoing)

        given:
        outgoing.dispatch({ it instanceof ChannelMessage }) >> { ChannelMessage message ->
            messages.add(message.payload)
            if (messages.size() == 20) {
                instant.messagesReceived
            }
        }

        and:
        hub.addConnection(connection)

        when:
        def dispatcher = hub.getOutgoing("channel", Long)
        20.times { dispatcher.dispatch(it) }
        thread.blockUntil.messagesReceived

        then:
        messages == 0..19

        cleanup:
        connection.stop()
    }

    def "each outgoing message is dispatched to exactly one connection"() {
        def messages = new CopyOnWriteArrayList()
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection1 = new MockOutgoingConnection(outgoing)
        def connection2 = new MockOutgoingConnection(outgoing)

        given:
        outgoing.dispatch({ it instanceof ChannelMessage }) >> { ChannelMessage message ->
            messages.add(message.payload)
            if (messages.size() == 20) {
                instant.messagesReceived
            }
        }

        and:
        hub.addConnection(connection1)
        hub.addConnection(connection2)

        when:
        def dispatcher = hub.getOutgoing("channel", Long)
        20.times { dispatcher.dispatch(it) }
        thread.blockUntil.messagesReceived

        then:
        new ArrayList<String>(messages).sort() == 0..19

        cleanup:
        connection1.stop()
        connection2.stop()
    }

    def "stops dispatching outgoing messages to failed connection"() {
        RemoteConnection<InterHubMessage> outgoing = Mock()
        def connection = new MockOutgoingConnection(outgoing)
        def dispatch = hub.getOutgoing("channel", String)
        def failure = new RuntimeException()

        given:
        dispatch.dispatch("message 1")
        dispatch.dispatch("message 2")

        when:
        hub.addConnection(connection)
        thread.blockUntil.broken

        then:
        1 * outgoing.dispatch({ it.payload == "message 1" }) >> {
            throw failure
        }
        1 * errorHandler.execute(failure) >> {
            instant.broken
        }
        0 * _._

        cleanup:
        connection.stop()
    }

    def "incoming messages are dispatched asynchronously to handler"() {
        def connection = new TestConnection()
        Dispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)

        when:
        hub.addConnection(connection)
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
        thread.blockUntil.message1Received
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 2"))
        connection.stop()
        thread.blockUntil.message2Received

        then:
        1 * handler.dispatch("message 1") >> {
            instant.message1Received
            thread.block()
        }
        1 * handler.dispatch("message 2") >> {
            instant.message2Received
        }
        0 * _._

        and:
        instant.message1Received < instant.message2Received
    }

    def "queued incoming messages are dispatched when handler added"() {
        def connection = new TestConnection()
        Dispatch<String> handler = Mock()

        given:
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 2"))
        hub.addConnection(connection)

        when:
        hub.addHandler("channel", handler)
        thread.blockUntil.message2Received

        then:
        1 * handler.dispatch("message 1") >> {
            instant.message1Received
        }
        1 * handler.dispatch("message 2") >> {
            instant.message2Received
        }
        0 * _._

        and:
        instant.message1Received < instant.message2Received

        cleanup:
        connection.stop()
    }

    def "each incoming message is dispatched to exactly one handler"() {
        def connection = new TestConnection()
        def messages = new CopyOnWriteArrayList()
        Dispatch<Long> handler1 = Mock()
        Dispatch<Long> handler2 = Mock()

        given:
        handler1.dispatch(_) >> { messages << it[0] }
        handler2.dispatch(_) >> { messages << it[0] }
        hub.addHandler("channel", handler1)
        hub.addHandler("channel", handler2)

        when:
        hub.addConnection(connection)
        20.times { connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), it)) }
        connection.stop()
        hub.stop()

        then:
        messages as Set == (0..19) as Set
    }

    def "incoming messages are dispatched to handler for the appropriate channel"() {
        def connection = new TestConnection()
        Dispatch<String> handler1 = Mock()
        Dispatch<String> handler2 = Mock()

        given:
        hub.addHandler("channel 1", handler1)
        hub.addHandler("channel 2", handler2)
        hub.addConnection(connection)

        when:
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel 1"), "message 1"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel 2"), "message 2"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel 1"), "message 3"))
        thread.blockUntil.channel1Received
        thread.blockUntil.channel2Received

        then:
        1 * handler1.dispatch("message 1")
        1 * handler1.dispatch("message 3") >> { instant.channel1Received }
        1 * handler2.dispatch("message 2") >> { instant.channel2Received }
        0 * _._

        cleanup:
        connection.stop()
    }

    def "stops dispatching to failed handler"() {
        def connection = new TestConnection()
        def failure = new RuntimeException()
        Dispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)
        hub.addConnection(connection)

        when:
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 2"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 3"))
        thread.blockUntil.failed

        then:
        1 * handler.dispatch("message 1") >> { throw failure }
        1 * errorHandler.execute(failure) >> {
            instant.failed
        }
        0 * _._

        cleanup:
        connection.stop()
    }

    def "stop blocks until queued incoming messages handled"() {
        def connection = new TestConnection()
        Dispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)
        hub.addConnection(connection)

        when:
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 2"))
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 3"))
        connection.stop()
        hub.stop()

        then:
        1 * handler.dispatch("message 1") >> { thread.block() }
        1 * handler.dispatch("message 2")
        1 * handler.dispatch("message 3")
        0 * _._
    }

    def "queued outgoing messages are dispatched asynchronously to rejected message listener when stop requested and no connection available"() {
        RejectedMessageListener listener = Mock()

        given:
        hub.addHandler("channel", listener)
        def dispatcher = hub.getOutgoing("channel", String)
        dispatcher.dispatch("message 1")
        dispatcher.dispatch("message 2")

        when:
        operation.requestStop {
            hub.requestStop()
        }
        hub.stop()

        then:
        1 * listener.messageDiscarded("message 1") >> {
            thread.block()
            instant.message1Handled
        }
        1 * listener.messageDiscarded("message 2") >> {
            instant.message2Handled
        }
        0 * _._

        and:
        operation.requestStop.end < instant.message1Handled
        instant.message1Handled < instant.message2Handled
    }

    def "rejected message listener is not notified when no queued outgoing messages when stop requested"() {
        RejectedMessageListener listener = Mock()

        given:
        hub.addHandler("channel", listener)

        when:
        hub.stop()

        then:
        0 * _._
    }

    def "rejected message listener is notified only of rejected outgoing messages on associated channel"() {
        RejectedMessageListener listener = Mock()

        given:
        hub.addHandler("channel", listener)
        hub.getOutgoing("other", String).dispatch("ignore me")
        hub.getOutgoing("channel", String).dispatch("discarded")

        when:
        hub.stop()

        then:
        1 * listener.messageDiscarded("discarded")
        0 * _._
    }

    def "stops dispatching rejected outgoing messages to failed listener"() {
        RejectedMessageListener listener = Mock()
        def dispatch = hub.getOutgoing("channel", String)
        def failure = new RuntimeException()

        given:
        dispatch.dispatch("message 1")
        dispatch.dispatch("message 2")
        hub.addHandler("channel", listener)

        when:
        hub.stop()

        then:
        1 * listener.messageDiscarded("message 1") >> {
            throw failure
        }
        1 * errorHandler.execute(failure)
        0 * _._
    }

    def "only handlers that implement RejectedMessageListener are notified of rejected outgoing messages"() {
        RejectedMessageListener listener1 = Mock()
        RejectedMessageListener listener2 = Mock()

        given:
        hub.addHandler("channel", listener1)
        hub.addHandler("channel", listener2)
        hub.addHandler("channel", new Object())
        hub.getOutgoing("channel", String).dispatch("discarded")

        when:
        hub.stop()

        then:
        1 * listener1.messageDiscarded("discarded")
        1 * listener2.messageDiscarded("discarded")
        0 * _._
    }

    def "notifies handler that the end of incoming messages has been reached when stop requested and no connections"() {
        BoundedDispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)

        when:
        operation.request {
            hub.requestStop()
        }
        thread.blockUntil.notified

        then:
        1 * handler.endStream() >> {
            thread.blockUntil.request
            instant.notified
        }
        0 * _._
    }

    def "notifies handler that the end of incoming messages has been reached when no connections added and no further connections signalled"() {
        BoundedDispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)

        when:
        operation.signal {
            hub.noFurtherConnections()
        }
        thread.blockUntil.notified

        then:
        1 * handler.endStream() >> {
            thread.blockUntil.signal
            instant.notified
        }
        0 * _._
    }

    def "notifies handler that the end of incoming messages has been reached when stop requested and end-of-stream reached for all connections"() {
        BoundedDispatch<String> handler = Mock()
        def connection1 = new TestConnection()
        def connection2 = new TestConnection()

        given:
        hub.addHandler("channel", handler)
        hub.addConnection(connection1)
        hub.addConnection(connection2)

        when:
        operation.request {
            connection1.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
            connection1.stop()
            hub.requestStop()
            thread.block()
            connection2.stop()
        }
        thread.blockUntil.notified

        then:
        1 * handler.dispatch("message 1")

        then:
        1 * handler.endStream() >> {
            thread.blockUntil.request
            instant.notified
        }
        0 * _._
    }

    def "notifies handler that the end of incoming messages has been reached when end-of-stream reached for all connections and no further connections signalled"() {
        BoundedDispatch<String> handler = Mock()
        def connection1 = new TestConnection()
        def connection2 = new TestConnection()

        given:
        hub.addHandler("channel", handler)
        hub.addConnection(connection1)
        hub.addConnection(connection2)

        when:
        operation.request {
            connection1.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
            connection1.stop()
            hub.noFurtherConnections()
            thread.block()
            connection2.stop()
        }
        thread.blockUntil.notified

        then:
        1 * handler.dispatch("message 1")

        then:
        1 * handler.endStream() >> {
            thread.blockUntil.request
            instant.notified
        }
        0 * _._
    }

    def "stop blocks until handle has finished processing end of incoming messages"() {
        BoundedDispatch<String> handler = Mock()
        def connection = new TestConnection()

        given:
        hub.addHandler("channel", handler)
        hub.addConnection(connection)

        when:
        connection.queueIncoming(new ChannelMessage(new ChannelIdentifier("channel"), "message 1"))
        connection.stop()

        operation.request {
            hub.stop()
        }

        then:
        operation.request.end > instant.notified

        and:
        1 * handler.dispatch("message 1")
        1 * handler.endStream() >> {
            thread.block()
            instant.notified
        }
        0 * _._
    }

    def "cannot dispatch outgoing messages after stop requested"() {
        given:
        def dispatcher = hub.getOutgoing("channel", String)

        when:
        hub.requestStop()
        dispatcher.dispatch("message 1")

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot dispatch message, as <hub> has been stopped.'
    }

    def "cannot create dispatcher after stop started"() {
        given:
        hub.getOutgoing("channel", String)

        when:
        hub.requestStop()
        hub.getOutgoing("channel", String)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot create outgoing dispatch, as <hub> has been stopped.'
    }

    def "cannot add handler after stop started"() {
        when:
        hub.requestStop()
        hub.addHandler("channel", new Object())

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot add handler, as <hub> has been stopped.'
    }

    def "cannot add connection after stop started"() {
        when:
        hub.requestStop()
        hub.addConnection(Mock(RemoteConnection))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot add connection, as <hub> has been stopped.'
    }

    def "stop and request stop do nothing when already stopped"() {
        when:
        hub.stop()
        hub.stop()
        hub.requestStop()

        then:
        0 * _._
    }

    private static class MockOutgoingConnection implements RemoteConnection<InterHubMessage> {
        private final RemoteConnection<InterHubMessage> dispatch
        private final BlockingQueue<InterHubMessage> incoming = new LinkedBlockingQueue<>()

        MockOutgoingConnection(RemoteConnection<InterHubMessage> outgoing) {
            this.dispatch = outgoing
        }

        void dispatch(InterHubMessage message) {
            dispatch.dispatch(message)
        }

        @Override
        void flush() {
            dispatch.flush()
        }

        InterHubMessage receive() {
            return incoming.take()
        }

        void stop() {
            incoming.put(new EndOfStream())
        }
    }
}
