/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.context

import org.gradle.messaging.remote.Address
import org.gradle.util.Matchers
import spock.lang.Specification

class DaemonAddressTest extends Specification {
    def "equals and hashcode"() {
        def id1 = "id-1"
        def id2 = "id-2"
        def address1 = Stub(Address)
        def address2 = Stub(Address)

        def daemonAddress = new DaemonAddress(id1, address1)
        def same = new DaemonAddress(id1, address1)
        def differentId = new DaemonAddress(id2, address1)
        def differentAddress = new DaemonAddress(id1, address2)

        expect:
        daemonAddress Matchers.strictlyEqual(same)
        daemonAddress != differentId
        daemonAddress != differentAddress
    }
}
