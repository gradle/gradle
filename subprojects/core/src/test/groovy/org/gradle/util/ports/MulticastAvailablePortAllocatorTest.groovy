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

class MulticastAvailablePortAllocatorTest extends Specification {
    def "no public constructors on MulticastAvailablePortAllocator"() {
        def constructors = MulticastAvailablePortAllocator.getConstructors()

        expect:
        constructors.size() == 0
    }

    def "can assign a port" () {
        MulticastAvailablePortAllocator portAllocator = MulticastAvailablePortAllocator.getInstance()

        when:
        def port = portAllocator.assignPort()

        then:
        port >= PortAllocator.MIN_PRIVATE_PORT
        port <= PortAllocator.MAX_PRIVATE_PORT

        and:
        portAllocator.releasePort(port)
    }
}
