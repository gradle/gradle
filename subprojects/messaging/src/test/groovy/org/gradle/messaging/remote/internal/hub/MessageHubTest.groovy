package org.gradle.messaging.remote.internal.hub

import org.gradle.api.Action
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArraySet

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
        Connection<InterHubMessage> connection = Mock()

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
        1 * connection.dispatch({ it.payload == "message1" }) >> {
            thread.blockUntil.dispatch
            instant.message1Dispatched
        }
        1 * connection.dispatch({ it.payload == "message2" }) >> {
            instant.message2Dispatched
        }
        1 * connection.dispatch({ it.payload == 12 }) >> {
            instant.longDispatched
        }
        0 * _._

        and:
        operation.dispatch.end < instant.message1Dispatched
        instant.message1Dispatched < instant.message2Dispatched
        instant.message2Dispatched < instant.longDispatched
    }

    def "queued outgoing messages are dispatched asynchronously to connection when connection is added"() {
        Connection<InterHubMessage> connection = Mock()

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
        1 * connection.dispatch({ it.payload == "message1" }) >> {
            thread.blockUntil.connection
            instant.message1Dispatched
        }
        1 * connection.dispatch({ it.payload == "message2" }) >> {
            instant.message2Dispatched
        }
        1 * connection.dispatch({ it.payload == 12 }) >> {
            instant.longDispatched
        }
        1 * connection.receive() >> null
        0 * _._

        and:
        operation.connection.end < instant.message1Dispatched
        instant.message1Dispatched < instant.message2Dispatched
        instant.message2Dispatched < instant.longDispatched
    }

    def "stop blocks until all outgoing messages dispatched to connection"() {
        Connection<InterHubMessage> connection = Mock()

        given:
        hub.addConnection(connection)

        when:
        hub.getOutgoing("channel1", String).dispatch("message1")
        hub.getOutgoing("channel1", String).dispatch("message2")
        hub.getOutgoing("channel2", Long).dispatch(12)
        hub.stop()

        then:
        1 * connection.dispatch({ it.payload == "message1" })
        1 * connection.dispatch({ it.payload == "message2" })
        1 * connection.dispatch({ it.payload == 12 })
        0 * _._
    }

    def "each outgoing message is dispatched to exactly one connection"() {
        def messages = new CopyOnWriteArraySet()
        Connection<InterHubMessage> connection1 = Mock()
        Connection<InterHubMessage> connection2 = Mock()

        given:
        connection1.dispatch(_) >> { ChannelMessage message ->
            messages.add(message.payload)
        }
        connection2.dispatch(_) >> { ChannelMessage message ->
            messages.add(message.payload)
        }

        and:
        hub.addConnection(connection1)
        hub.addConnection(connection2)

        when:
        def dispatcher = hub.getOutgoing("channel", Long)
        20.times { dispatcher.dispatch(it) }
        hub.stop()

        then:
        messages == ((0..19) as Set)
    }

    def "stops dispatching outgoing messages to failed connection"() {
        Connection<InterHubMessage> connection = Mock()
        def dispatch = hub.getOutgoing("channel", String)
        def failure = new RuntimeException()

        given:
        dispatch.dispatch("message 1")
        dispatch.dispatch("message 2")

        when:
        hub.addConnection(connection)
        hub.stop()

        then:
        1 * connection.dispatch({ it.payload == "message 1" }) >> {
            throw failure
        }
        1 * connection.receive() >> null
        1 * errorHandler.execute(failure)
        0 * _._
    }

    def "incoming messages are dispatched asynchronously to handler"() {
        Connection<InterHubMessage> connection = Mock()
        Dispatch<String> handler = Mock()

        given:
        hub.addHandler("channel", handler)

        when:
        hub.addConnection(connection)
        thread.blockUntil.message2Received

        then:
        1 * connection.receive() >> new ChannelMessage(new ChannelIdentifier("channel"), "message 1")
        1 * connection.receive() >> {
            thread.blockUntil.message1Received
            new ChannelMessage(new ChannelIdentifier("channel"), "message 2")
        }
        1 * connection.receive() >> null
        1 * handler.dispatch("message 1") >> {
            instant.message1Received
            thread.block()
        }
        1 * handler.dispatch("message 2") >> {
            instant.message2Received
        }
        0 * _._
    }

    def "queued incoming messages are dispatched when handler added"() {
        expect: false
    }

    def "stop blocks until queued incoming messages handled"() {
        expect: false
    }

    def "queued incoming messages are dispatched asynchronously to rejected message listener when stop requested and no handler available"() {
        expect: false
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
        hub.addConnection(Mock(Connection))

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
}
