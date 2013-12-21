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
package org.gradle.messaging.remote.internal.inet

import org.gradle.util.Matchers
import spock.lang.Specification

class MultiChoiceAddressTest extends Specification {
    def "has useful display name"() {
        InetAddress candidate = Mock()
        def address = new MultiChoiceAddress('<canonical>', 1234, [candidate])

        given:
        candidate.toString() >> '<address>'

        expect:
        address.displayName == '[<canonical> port:1234, addresses:[<address>]]'
        address.toString() == '[<canonical> port:1234, addresses:[<address>]]'
    }

    def "addresses are equal when their canonical addresses are equal"() {
        InetAddress address1 = Mock()
        InetAddress address2 = Mock()
        def address = new MultiChoiceAddress('canonical', 1234, [address1])
        def same = new MultiChoiceAddress('canonical', 1234, [address1])
        def differentPort = new MultiChoiceAddress('canonical', 1567, [address1])
        def differentCandidates = new MultiChoiceAddress('canonical', 1234, [address2])
        def differentCanonical = new MultiChoiceAddress('other', 1234, [address1])

        expect:
        address Matchers.strictlyEqual(same)
        address Matchers.strictlyEqual(differentCandidates)
        address Matchers.strictlyEqual(differentPort)
        address != differentCanonical
    }
}
