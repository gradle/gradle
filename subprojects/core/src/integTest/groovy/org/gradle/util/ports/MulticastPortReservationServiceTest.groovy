/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports

import org.gradle.messaging.remote.internal.inet.SocketInetAddress
import org.gradle.util.ConcurrentSpecification
import spock.lang.Ignore

import java.util.concurrent.CountDownLatch

@Ignore
class MulticastPortReservationServiceTest extends ConcurrentSpecification {
    PortAllocator portAllocator = Mock(PortAllocator)
    PortAllocator peerPortAllocator = Mock(PortAllocator)
    MulticastPortReservationService service = new MulticastPortReservationService(portAllocator, testBroadcastAddress)
    MulticastPortReservationService peerService = new MulticastPortReservationService(peerPortAllocator, testBroadcastAddress)

    def cleanup() {
        service.stop()
        peerService.stop()
    }

    static SocketInetAddress getTestBroadcastAddress() {
        new SocketInetAddress(InetAddress.getByName("233.253.17.122"), 7915)
    }

    def "peers can reserve ports" () {
        def action = startsAsyncAction()

        when:
        action.started {
            peerService.reservePorts(1, 10)
        }

        then:
        1 * portAllocator.peerReservation(1, 10) >> { action.done() }
    }

    def "can reserve ports with peers" () {
        def action = startsAsyncAction()

        when:
        action.started {
            service.reservePorts(1, 10)
        }

        then:
        1 * peerPortAllocator.peerReservation(1, 10) >> { action.done() }
    }

    def "peers can release ports" () {
        def action = startsAsyncAction()

        when:
        action.started {
            peerService.releasePorts(1, 10)
        }

        then:
        1 * portAllocator.releasePeerReservation(1, 10) >> { action.done() }
    }

    def "can release ports with peers" () {
        def action = startsAsyncAction()

        when:
        action.started {
            service.releasePorts(1, 10)
        }

        then:
        1 * peerPortAllocator.releasePeerReservation(1, 10) >> { action.done() }
    }

    def "can reserve ports with multiple peers" () {
        PortAllocator peerPortAllocator2 = Mock(PortAllocator)
        PortAllocator peerPortAllocator3 = Mock(PortAllocator)
        MulticastPortReservationService peerService2 = new MulticastPortReservationService(peerPortAllocator2, testBroadcastAddress)
        MulticastPortReservationService peerService3 = new MulticastPortReservationService(peerPortAllocator3, testBroadcastAddress)

        def broadcast = new CountDownLatch(3)

        when:
        start {
            service.reservePorts(1, 10)
            broadcast.await()
        }
        finished()

        then:
        1 * peerPortAllocator.peerReservation(1, 10) >> { broadcast.countDown() }
        1 * peerPortAllocator2.peerReservation(1, 10) >> { broadcast.countDown() }
        1 * peerPortAllocator3.peerReservation(1, 10) >> { broadcast.countDown() }

        cleanup:
        peerService2.stop()
        peerService3.stop()
    }

    def "can release ports with multiple peers" () {
        PortAllocator peerPortAllocator2 = Mock(PortAllocator)
        PortAllocator peerPortAllocator3 = Mock(PortAllocator)
        MulticastPortReservationService peerService2 = new MulticastPortReservationService(peerPortAllocator2, testBroadcastAddress)
        MulticastPortReservationService peerService3 = new MulticastPortReservationService(peerPortAllocator3, testBroadcastAddress)

        def broadcast = new CountDownLatch(3)

        when:
        start {
            service.releasePorts(1, 10)
            broadcast.await()
        }
        finished()

        then:
        1 * peerPortAllocator.releasePeerReservation(1, 10) >> { broadcast.countDown() }
        1 * peerPortAllocator2.releasePeerReservation(1, 10) >> { broadcast.countDown() }
        1 * peerPortAllocator3.releasePeerReservation(1, 10) >> { broadcast.countDown() }

        cleanup:
        peerService2.stop()
        peerService3.stop()
    }

    def "new peers receive port reservations from existing peers" () {
        PortAllocator peerPortAllocator2 = Mock(PortAllocator)
        MulticastPortReservationService peerService2 = new MulticastPortReservationService(peerPortAllocator2, testBroadcastAddress)
        def broadcast = new CountDownLatch(2)

        when:
        start {
            service.start()
            broadcast.await()
        }
        finished()

        then:
        1 * peerPortAllocator.getReservations() >> { [ new ReservedPortRange(1, 10)] }
        1 * peerPortAllocator2.getReservations() >> { [ new ReservedPortRange(11, 20)] }

        and:
        1 * portAllocator.peerReservation(1, 10) >> { broadcast.countDown() }
        1 * portAllocator.peerReservation(11, 20) >> { broadcast.countDown() }

        cleanup:
        peerService2?.stop()
    }
}
