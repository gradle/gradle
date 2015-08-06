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

class PeerAwareAvailablePortAllocatorTest extends AbstractPortAllocatorTest {
    PeerAwareAvailablePortAllocator portAllocator = PeerAwareAvailablePortAllocator.getInstance()

    def setup() {
        portAllocator.portRangeFactory = portRangeFactory
    }

    def cleanup() {
        portAllocator.clear()
    }

    def "peer allocated port ranges will not be assigned"() {
        when:
        portAllocator.peerReservation(PortAllocator.MIN_PRIVATE_PORT, PortAllocator.MAX_PRIVATE_PORT)
        portAllocator.assignPort()

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        def e = thrown(NoSuchElementException)
        e.message == "Unable to find a port range to reserve"
    }

    def "peer allocated ports can be reused after being released"() {
        when:
        portAllocator.peerReservation(PortAllocator.MIN_PRIVATE_PORT, PortAllocator.MIN_PRIVATE_PORT + portAllocator.rangeSize)
        portAllocator.peerReservation(PortAllocator.MIN_PRIVATE_PORT + portAllocator.rangeSize, PortAllocator.MAX_PRIVATE_PORT)
        portAllocator.assignPort()

        then:
        2 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        thrown(NoSuchElementException)

        when:
        portAllocator.releasePeerReservation(PortAllocator.MIN_PRIVATE_PORT, PortAllocator.MIN_PRIVATE_PORT + portAllocator.rangeSize)
        def port = portAllocator.assignPort()

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        port >= PortAllocator.MIN_PRIVATE_PORT
        port <= PortAllocator.MIN_PRIVATE_PORT + portAllocator.rangeSize
    }
}
