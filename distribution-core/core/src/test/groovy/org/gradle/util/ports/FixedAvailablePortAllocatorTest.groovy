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
    def "assigns a unique fixed port range based on worker id (totalWorkers: #totalWorkers, totalAgents: #totalAgents)" () {
        int rangeSize = FixedAvailablePortAllocator.DEFAULT_RANGE_SIZE - 1
        def portAllocators = (1..totalAgents).collect { agentNum ->
            (1..totalWorkers).collect { workerId ->
                def portAllocator = new FixedAvailablePortAllocator(workerId, agentNum, totalAgents)
                portAllocator.assignPort()
                return portAllocator
            }
        }

        expect:
        (1..totalAgents).each { agentNum ->
            (1..totalWorkers).each { workerNum ->
                def portAllocator = portAllocators[agentNum - 1].remove(0)
                def otherRanges = portAllocators.flatten()
                assert portAllocator.reservations.size() == 1
                assert portAllocator.reservations[0].endPort - portAllocator.reservations[0].startPort == rangeSize
                assert portAllocator.reservations[0].startPort >= PortAllocator.MIN_PRIVATE_PORT
                assert portAllocator.reservations[0].endPort <= PortAllocator.MAX_PRIVATE_PORT
                otherRanges.each { other ->
                    assert !isReservedInList(other.reservations, portAllocator.reservations[0].startPort, portAllocator.reservations[0].endPort)
                }
            }
        }

        where:
        totalWorkers | totalAgents
        2            | 1
        2            | 2
        4            | 1
        4            | 2
        8            | 1
        8            | 2
        8            | 4
    }

    @Unroll
    def "port range allocation wraps around when workerId exceeds buckets per agent (overMax: #overMax, agentNum: #agentNum, totalAgents: #totalAgents)" () {
        int bucketsPerAgent = (PortAllocator.MAX_PRIVATE_PORT - PortAllocator.MIN_PRIVATE_PORT) / (FixedAvailablePortAllocator.DEFAULT_RANGE_SIZE * totalAgents)
        int workerId = bucketsPerAgent + overMax
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(workerId, agentNum, totalAgents)
        FixedAvailablePortAllocator samePortAllocator = new FixedAvailablePortAllocator(overMax, agentNum, totalAgents)

        when:
        portAllocator.assignPort()
        samePortAllocator.assignPort()

        then:
        portAllocator.reservations[0].startPort == samePortAllocator.reservations[0].startPort
        portAllocator.reservations[0].endPort == samePortAllocator.reservations[0].endPort

        where:
        overMax  | agentNum | totalAgents
        5        | 1        | 1
        6        | 1        | 1
        5        | 1        | 2
        11       | 2        | 2
        12       | 1        | 2
        9        | 1        | 4
        13       | 3        | 4
    }

    def "uses first bucket when workerId is not available" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(-1, 1, 1)

        when:
        portAllocator.assignPort()

        then:
        portAllocator.reservations.size() == 1
        portAllocator.reservations.get(0).startPort == PortAllocator.MIN_PRIVATE_PORT
        portAllocator.reservations.get(0).endPort == PortAllocator.MIN_PRIVATE_PORT + FixedAvailablePortAllocator.DEFAULT_RANGE_SIZE - 1
    }

    def "throws an exception when all ports in range are exhausted" () {
        ReservedPortRangeFactory portRangeFactory = Mock(ReservedPortRangeFactory)
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(1, 1, 1)
        portAllocator.portRangeFactory = portRangeFactory

        when:
        FixedAvailablePortAllocator.DEFAULT_RANGE_SIZE.times {
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
