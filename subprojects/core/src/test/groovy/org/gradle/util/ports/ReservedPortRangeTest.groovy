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


class ReservedPortRangeTest extends Specification {
    ReservedPortRange range = new ReservedPortRange(1, 10)
    PortDetector portDetector = Mock(PortDetector)

    def setup() {
        range.portDetector = portDetector
    }

    def "can allocate unique ports in port range"() {
        def reserved = []

        when:
        10.times {
            reserved.add(range.allocate())
        }

        then:
        10 * portDetector.isAvailable(_) >> { true }

        and:
        reserved.sort() == 1..10
    }

    def "cannot allocate more ports than in range" () {
        when:
        10.times {
            range.allocate()
        }

        then:
        10 * portDetector.isAvailable(_) >> { true }

        and:
        range.allocate()  == -1
    }

    def "detects when ports are unavailable" () {
        def reserved = []

        when:
        8.times {
            reserved.add(range.allocate())
        }

        then:
        _ * portDetector.isAvailable(_) >> { int port -> return ! (port in [3,7]) }

        and:
        ! reserved.contains(3)
        ! reserved.contains(7)

        and:
        range.allocate() == -1
    }

    def "can deallocate ports" () {
        def reserved = []
        _ * portDetector.isAvailable(_) >> { true }

        when:
        4.times {
            reserved.add(range.allocate())
        }

        then:
        range.unallocated.size() == 6
        range.allocated.size() == 4

        when:
        reserved.each { int port ->
            range.deallocate(port)
        }

        then:
        range.unallocated.size() == 10
        range.allocated.size() == 0
    }

    def "can reuse deallocated ports" () {
        _ * portDetector.isAvailable(_) >> { true }

        when:
        10.times {
            range.allocate()
        }

        then:
        range.allocate() == -1

        when:
        range.deallocate(3)
        range.deallocate(9)
        range.deallocate(2)

        then:
        (0..2).collect { range.allocate() }.sort() == [2, 3, 9]
    }
}
