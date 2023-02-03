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


abstract class AbstractPortAllocatorTest extends Specification {
    final PortDetector noPortsAvailable = Stub(PortDetector) { isAvailable(_) >> { false } }
    final PortDetector portsAlwaysAvailable = Stub(PortDetector) { isAvailable(_) >> { true } }

    ReservedPortRangeFactory portRangeFactory = Mock(ReservedPortRangeFactory)

    static ReservedPortRange getPortRange(PortDetector portDetector, int startPort, int endPort) {
        ReservedPortRange portRange = new ReservedPortRange(startPort, endPort)
        portRange.portDetector = portDetector
        return portRange
    }

    static boolean isReservedInList(List<ReservedPortRange> reservationList, int startPort, int endPort) {
        for (int i=0; i<reservationList.size(); i++) {
            ReservedPortRange range = reservationList.get(i)
            if ((startPort <= range.endPort && startPort >= range.startPort)
                || (endPort >= range.startPort && endPort <= range.endPort)) {
                return true
            }
        }
        return false
    }

}
