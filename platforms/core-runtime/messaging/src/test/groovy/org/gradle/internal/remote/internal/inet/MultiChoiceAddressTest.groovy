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
package org.gradle.internal.remote.internal.inet

import org.gradle.util.Matchers
import spock.lang.Specification

class MultiChoiceAddressTest extends Specification {
    def "has useful display name"() {
        InetAddress candidate = InetAddress.getByName("1.1.1.1")
        UUID uuid = UUID.randomUUID()
        def address = new MultiChoiceAddress(uuid, 1234, [candidate])

        expect:
        address.displayName == "[${uuid} port:1234, addresses:[/1.1.1.1]]"
        address.toString() == address.displayName
    }

    def "addresses are equal when their canonical id and port and candidate addresses are equal"() {
        InetAddress address1 = InetAddress.getByName("1.1.1.1")
        InetAddress address2 = InetAddress.getByName("1.0.0.1")
        UUID id = UUID.randomUUID()
        UUID otherId = UUID.randomUUID()
        def address = new MultiChoiceAddress(id, 1234, [address1])
        def same = new MultiChoiceAddress(id, 1234, [address1])
        def differentPort = new MultiChoiceAddress(id, 1567, [address1])
        def differentCandidates = new MultiChoiceAddress(id, 1234, [address2])
        def differentCanonical = new MultiChoiceAddress(otherId, 1234, [address1])

        expect:
        address Matchers.strictlyEqual(same)
        address != differentCandidates
        address != differentPort
        address != differentCanonical
    }
}
