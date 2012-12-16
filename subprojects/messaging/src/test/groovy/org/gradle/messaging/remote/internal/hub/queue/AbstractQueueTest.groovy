package org.gradle.messaging.remote.internal.hub.queue

import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import spock.lang.Specification

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

abstract class AbstractQueueTest extends Specification {
    final Condition broken = Stub() {
        await() >> { throw new UnsupportedOperationException("should not be waiting") }
    }
    final Lock lock = Stub() {
        newCondition() >> broken
    }

    def unicast() {
        InterHubMessage message = Stub() {
            getDelivery() >> InterHubMessage.Delivery.SingleHandler
        }
        return message
    }

    def broadcast() {
        InterHubMessage message = Stub() {
            getDelivery() >> InterHubMessage.Delivery.AllHandlers
        }
        return message
    }

    def stateful() {
        InterHubMessage message = Stub() {
            getDelivery() >> InterHubMessage.Delivery.Stateful
        }
        return message
    }
}
