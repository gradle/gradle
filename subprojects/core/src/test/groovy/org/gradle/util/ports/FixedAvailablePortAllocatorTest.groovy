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
    def "assigns a fixed port range based on worker id (maxForks: #maxForks, workerId: #workerId, agentNum: #agentNum, totalAgents: #totalAgents)" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(maxForks, workerId, agentNum, totalAgents)

        when:
        portAllocator.assignPort()

        then:
        portAllocator.reservations.size() == 1
        portAllocator.reservations.get(0).startPort == startPort
        portAllocator.reservations.get(0).endPort == endPort

        where:
        maxForks | workerId | agentNum | totalAgents | startPort | endPort
        2        | 1        | 1        | 2           | 49152     | 53246
        2        | 2        | 1        | 2           | 53247     | 57341
        2        | 1        | 2        | 2           | 57342     | 61436
        2        | 2        | 2        | 2           | 61437     | 65531
        2        | 5        | 2        | 2           | 57342     | 61436

        4        | 1        | 1        | 1           | 49152     | 53246
        4        | 2        | 1        | 1           | 53247     | 57341
        4        | 3        | 1        | 1           | 57342     | 61436
        4        | 4        | 1        | 1           | 61437     | 65531
        4        | 10       | 1        | 1           | 53247     | 57341

        4        | 1        | 1        | 2           | 49152     | 51198
        4        | 2        | 1        | 2           | 51199     | 53245
        4        | 3        | 1        | 2           | 53246     | 55292
        4        | 4        | 1        | 2           | 55293     | 57339
        4        | 11       | 1        | 2           | 53246     | 55292
        4        | 1        | 2        | 2           | 57340     | 59386
        4        | 2        | 2        | 2           | 59387     | 61433
        4        | 3        | 2        | 2           | 61434     | 63480
        4        | 4        | 2        | 2           | 63481     | 65527
        4        | 10       | 2        | 2           | 59387     | 61433

        6        | 1        | 1        | 2           | 49152     | 50516
        6        | 2        | 1        | 2           | 50517     | 51881
        6        | 3        | 1        | 2           | 51882     | 53246
        6        | 4        | 1        | 2           | 53247     | 54611
        6        | 5        | 1        | 2           | 54612     | 55976
        6        | 6        | 1        | 2           | 55977     | 57341
        6        | 7        | 1        | 2           | 49152     | 50516
        6        | 1        | 2        | 2           | 57342     | 58706
        6        | 2        | 2        | 2           | 58707     | 60071
        6        | 3        | 2        | 2           | 60072     | 61436
        6        | 4        | 2        | 2           | 61437     | 62801
        6        | 5        | 2        | 2           | 62802     | 64166
        6        | 6        | 2        | 2           | 64167     | 65531
        6        | 9        | 2        | 2           | 60072     | 61436
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
        def port1 = portAllocator.assignPort()

        then:
        port1 >= PortAllocator.MIN_PRIVATE_PORT
        port1 <= PortAllocator.MAX_PRIVATE_PORT

        when:
        def port2 = portAllocator.assignPort()

        then:
        port2 >= PortAllocator.MIN_PRIVATE_PORT
        port2 <= PortAllocator.MAX_PRIVATE_PORT

        and:
        port2 != port1

        cleanup:
        portAllocator.releasePort(port1)
        portAllocator.releasePort(port2)
    }
}
