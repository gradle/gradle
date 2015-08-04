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

import spock.lang.Specification

class AvailablePortAllocatorTest extends Specification {
    final PortDetector noPortsAvailable = Stub(PortDetector) { isAvailable(_) >> { false } }
    final PortDetector portsAlwaysAvailable = Stub(PortDetector) { isAvailable(_) >> { true } }

    ReservedPortRangeFactory portRangeFactory = Mock(ReservedPortRangeFactory)
    AvailablePortAllocator portAllocator = AvailablePortAllocator.getInstance()

    def setup() {
        portAllocator.portRangeFactory = portRangeFactory
    }

    def cleanup() {
        portAllocator.clear()
    }

    def "no public constructors on AvailablePortAllocator"() {
        def constructors = AvailablePortAllocator.getConstructors()

        expect:
        constructors.size() == 0
    }

    def "can assign a port"() {
        portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}

        expect:
        def port = portAllocator.assignPort()
        port >= PortAllocator.MIN_PRIVATE_PORT
        port <= PortAllocator.MAX_PRIVATE_PORT
    }

    def "assigns different ports"() {
        portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}

        expect:
        portAllocator.assignPort() != portAllocator.assignPort()
    }

    def "reserves another range once a range is exhausted"() {
        when:
        portAllocator.assignPort()

        then:
        // first range is exhausted
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(noPortsAvailable, startPort, endPort)}
        // another range is reserved
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}

        and:
        noExceptionThrown()
    }

    def "throws an exception when no more port ranges can be reserved"() {
        when:
        portAllocator.assignPort()

        then:
        _ * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(noPortsAvailable, startPort, endPort)}

        and:
        def e = thrown(NoSuchElementException)
        e.message == "Unable to find a port range to reserve"
    }

    def "can assign more ports than in a range"() {
        def assignments = PortAllocator.RANGE_SIZE + 1

        when:
        assignments.times {
            portAllocator.assignPort()
        }

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
    }

    def "releasing the last assigned port in a range shrinks the reserved ranges"() {
        def assignments = PortAllocator.RANGE_SIZE

        when:
        assignments.times {
            portAllocator.assignPort()
        }
        def port = portAllocator.assignPort()

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        portAllocator.reservations.size() == 2

        when:
        portAllocator.releasePort(port)

        then:
        portAllocator.reservations.size() == 1
    }

    def "releasing the last assigned port in the last range does not remove the reserved range"() {
        when:
        def port = portAllocator.assignPort()

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        portAllocator.reservations.size() == 1

        when:
        portAllocator.releasePort(port)

        then:
        portAllocator.reservations.size() == 1
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
        portAllocator.peerReservation(PortAllocator.MIN_PRIVATE_PORT, PortAllocator.MIN_PRIVATE_PORT + PortAllocator.RANGE_SIZE)
        portAllocator.peerReservation(PortAllocator.MIN_PRIVATE_PORT + PortAllocator.RANGE_SIZE, PortAllocator.MAX_PRIVATE_PORT)
        portAllocator.assignPort()

        then:
        2 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        thrown(NoSuchElementException)

        when:
        portAllocator.releasePeerReservation(PortAllocator.MIN_PRIVATE_PORT, PortAllocator.MIN_PRIVATE_PORT + PortAllocator.RANGE_SIZE)
        def port = portAllocator.assignPort()

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}
        port >= PortAllocator.MIN_PRIVATE_PORT
        port <= PortAllocator.MIN_PRIVATE_PORT + PortAllocator.RANGE_SIZE
    }

    static ReservedPortRange getPortRange(PortDetector portDetector, int startPort, int endPort) {
        ReservedPortRange portRange = new ReservedPortRange(startPort, endPort)
        portRange.portDetector = portDetector
        return portRange
    }
}
