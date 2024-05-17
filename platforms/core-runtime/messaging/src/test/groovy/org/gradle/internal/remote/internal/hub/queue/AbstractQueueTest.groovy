/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.remote.internal.hub.queue

import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
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

    def stateful() {
        InterHubMessage message = Stub() {
            getDelivery() >> InterHubMessage.Delivery.Stateful
        }
        return message
    }

    def broadcast() {
        InterHubMessage message = Stub() {
            getDelivery() >> InterHubMessage.Delivery.AllHandlers
        }
        return message
    }
}
