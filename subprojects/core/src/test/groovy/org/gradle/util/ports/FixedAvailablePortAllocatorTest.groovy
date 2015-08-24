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

import spock.lang.Unroll

class FixedAvailablePortAllocatorTest extends AbstractPortAllocatorTest {

    @Unroll
    def "assigns a unique fixed port range based on worker id (maxForks: #maxForks, totalAgents: #totalAgents)" () {
        int rangeSize = (PortAllocator.MAX_PRIVATE_PORT - PortAllocator.MIN_PRIVATE_PORT) / (maxForks * totalAgents) - 1
        def portAllocators = (1..totalAgents).collect { agentNum ->
            (1..maxForks).collect { workerId ->
                def portAllocator = new FixedAvailablePortAllocator(maxForks, workerId, agentNum, totalAgents)
                portAllocator.assignPort()
                return portAllocator
            }
        }

        expect:
        (1..totalAgents).each { agentNum ->
            (1..maxForks).each {
                def portAllocator = portAllocators[agentNum-1].remove(0)
                def otherRanges = portAllocators.flatten()
                assert portAllocator.reservations.size() == 1
                assert portAllocator.reservations[0].endPort - portAllocator.reservations[0].startPort == rangeSize
                assert portAllocator.reservations[0].startPort >= PortAllocator.MIN_PRIVATE_PORT
                assert portAllocator.reservations[0].endPort <= PortAllocator.MAX_PRIVATE_PORT
                otherRanges.each { other ->
                    assert !other.isReserved(portAllocator.reservations[0].startPort, portAllocator.reservations[0].endPort)
                }
            }
        }

        where:
        maxForks | totalAgents
        2        | 1
        2        | 2
        4        | 1
        4        | 2
        8        | 1
        8        | 2
        8        | 4
    }

    @Unroll
    def "port range allocation wraps around when workerId exceeds maxForks (maxForks: #maxForks, workerId: #workerId, sameAs: #sameAsRange)" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(maxForks, workerId, agentNum, totalAgents)
        FixedAvailablePortAllocator samePortAllocator = new FixedAvailablePortAllocator(maxForks, sameAsRange, agentNum, totalAgents)

        when:
        portAllocator.assignPort()
        samePortAllocator.assignPort()

        then:
        portAllocator.reservations[0].startPort == samePortAllocator.reservations[0].startPort
        portAllocator.reservations[0].endPort == samePortAllocator.reservations[0].endPort

        where:
        maxForks | workerId | agentNum | totalAgents | sameAsRange
        2        | 5        | 1        | 1           | 1
        2        | 6        | 1        | 1           | 2
        4        | 5        | 1        | 2           | 1
        4        | 10       | 1        | 2           | 2
        2        | 11       | 1        | 2           | 3
        4        | 12       | 1        | 2           | 4
        8        | 9        | 1        | 4           | 1
        8        | 13       | 1        | 4           | 5
    }

    def "uses all ports when maxForks and workerId are not available" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(1, -1, 1, 1)

        when:
        portAllocator.assignPort()

        then:
        portAllocator.reservations.size() == 1
        portAllocator.reservations.get(0).startPort == 49152
        portAllocator.reservations.get(0).endPort == 65534
    }

    def "throws an exception when all ports in range are exhausted" () {
        ReservedPortRangeFactory portRangeFactory = Mock(ReservedPortRangeFactory)
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(6, 1, 1, 1)
        portAllocator.portRangeFactory = portRangeFactory

        when:
        2730.times {
            portAllocator.assignPort()
        }

        then:
        1 * portRangeFactory.getReservedPortRange(_, _) >> {int startPort, int endPort -> getPortRange(portsAlwaysAvailable, startPort, endPort)}

        when:
        portAllocator.assignPort()

        then:
        def e = thrown(NoSuchElementException)
        e.message == "All available ports in the fixed port range for agent 1, worker 1 have been exhausted."
    }

    def "can assign ports from the static instance"() {
        PortAllocator portAllocator = FixedAvailablePortAllocator.getInstance()

        when:
        Integer port1 = portAllocator.assignPort()

        then:
        port1 >= PortAllocator.MIN_PRIVATE_PORT
        port1 <= PortAllocator.MAX_PRIVATE_PORT

        when:
        Integer port2 = portAllocator.assignPort()

        then:
        port2 >= PortAllocator.MIN_PRIVATE_PORT
        port2 <= PortAllocator.MAX_PRIVATE_PORT

        and:
        port2 != port1

        cleanup:
        port1 && portAllocator.releasePort(port1)
        port2 && portAllocator.releasePort(port2)
    }
}
