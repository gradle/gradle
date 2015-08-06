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
    def "no public constructors on AvailablePortAllocator"() {
        def constructors = AvailablePortAllocator.getConstructors()

        expect:
        constructors.size() == 0
    }

    @Unroll
    def "assigns a fixed port range based on worker id (maxForks: #maxForks, workerId: #workerId" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(maxForks, workerId)

        when:
        portAllocator.assignPort()

        then:
        portAllocator.reservations.size() == 1
        portAllocator.reservations.get(0).startPort == startPort
        portAllocator.reservations.get(0).endPort == endPort

        where:
        maxForks | workerId | startPort | endPort
        4        | 3        | 49152     | 53246
        4        | 4        | 53247     | 57341
        4        | 1        | 57342     | 61436
        4        | 2        | 61437     | 65531
        4        | 10       | 61437     | 65531
        6        | 5        | 49152     | 51881
        6        | 6        | 51882     | 54611
        6        | 1        | 54612     | 57341
        6        | 2        | 57342     | 60071
        6        | 3        | 60072     | 62801
        6        | 4        | 62802     | 65531
        6        | 7        | 54612     | 57341
    }

    def "uses all ports when maxForks and workerId are not available" () {
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(1, -1)

        when:
        portAllocator.assignPort()

        then:
        portAllocator.reservations.size() == 1
        portAllocator.reservations.get(0).startPort == 49152
        portAllocator.reservations.get(0).endPort == 65534
    }

    def "throws an exception when all ports in range are exhausted" () {
        ReservedPortRangeFactory portRangeFactory = Mock(ReservedPortRangeFactory)
        FixedAvailablePortAllocator portAllocator = new FixedAvailablePortAllocator(6, 1)
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
        e.message == "A fixed port range has already been assigned - cannot assign a new range."
    }
}
