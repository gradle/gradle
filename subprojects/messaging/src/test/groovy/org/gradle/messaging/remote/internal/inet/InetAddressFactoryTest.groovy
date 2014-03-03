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

package org.gradle.messaging.remote.internal.inet

import spock.lang.Specification

class InetAddressFactoryTest extends Specification {
    def factory = new InetAddressFactory()

    def "always contains at least one local address"() {
        expect:
        !factory.findLocalAddresses().empty
    }

    def "always contains at least one remote address"() {
        expect:
        !factory.findRemoteAddresses().empty
    }

    def "all local address is considered local"() {
        expect:
        factory.findLocalAddresses().every {
            factory.isLocal(it)
        }
    }

    def "no remote address is considered local"() {
        expect:
        factory.findRemoteAddresses().every {
            !factory.isLocal(it)
        }
    }
}
