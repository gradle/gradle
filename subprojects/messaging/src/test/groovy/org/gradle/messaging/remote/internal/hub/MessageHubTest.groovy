package org.gradle.messaging.remote.internal.hub

import org.gradle.api.Action
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

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

    def "queued messages are asynchronously forwarded to rejected message listener when stop requested"() {
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

    def "rejected message listener is not notified when no queued messages at stop requested"() {
        RejectedMessageListener listener = Mock()

        given:
        hub.addHandler("channel", listener)

        when:
        hub.stop()

        then:
        0 * _._
    }

    def "rejected message listener is notified only of rejected messages on associated channel"() {
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

    def "stops dispatching rejected messages to failed rejected message listener"() {
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

    def "only handlers that implement RejectedMessageListener are notified of rejected messages"() {
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

    def "cannot dispatch messages after stop requested"() {
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

    def "can stop and request stop do nothing when already stopped"() {
        when:
        hub.stop()
        hub.stop()
        hub.requestStop()

        then:
        0 * _._
    }
}
