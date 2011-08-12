/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.util

import spock.lang.Specification

class AvailablePortFinderTest extends Specification {
    AvailablePortFinder portFinder

    def "port range defaults to well-known and registered ports"() {
        when:
        portFinder = AvailablePortFinder.create()

        then:
        portFinder.fromPort == AvailablePortFinder.MIN_WELL_KNOWN_PORT
        portFinder.toPort == AvailablePortFinder.MAX_REGISTERED_PORT
    }

    def "can create port finder with private range"() {
        when:
        portFinder = AvailablePortFinder.createPrivate()

        then:
        portFinder.fromPort == AvailablePortFinder.MIN_PRIVATE_PORT
        portFinder.toPort == AvailablePortFinder.MAX_PRIVATE_PORT
    }

    def "can create port finder with custom range"() {
        when:
        portFinder = AvailablePortFinder.create(42, 12345)

        then:
        portFinder.fromPort == 42
        portFinder.toPort == 12345
    }

    def "cannot create port finder with invalid port range"() {
        when:
        portFinder = AvailablePortFinder.create(from, to)

        then:
        thrown(IllegalArgumentException)

        where:
        from | to
        -1   | 9
        1    | 65536
        5    | 3
    }

    def "can test for and find an available port"() {
        portFinder = AvailablePortFinder.createPrivate()

        expect:
        portFinder.available(portFinder.nextAvailable)
    }

    def "tries to return different ports on successive invocations"() {
        portFinder = AvailablePortFinder.createPrivate()

        expect:
        portFinder.nextAvailable != portFinder.nextAvailable
    }
}
