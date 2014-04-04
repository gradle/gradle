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

class InetAddressFactoryWithConfiguredBindAddressTest extends Specification {
    final def addressFactory = new InetAddressFactory()
    final def CONFIGURED_BIND_IP_ADDRESS = "127.1.2.3"
    final def CONFIGURED_BIND_ADDRESS = InetAddress.getByName(CONFIGURED_BIND_IP_ADDRESS)

    def setup() {
        System.setProperty(InetAddressFactory.BIND_ADDRESS_PROPERTY_KEY, CONFIGURED_BIND_IP_ADDRESS)
    }

    def "returns configured bind address"() {
        def bindAddress

        when: bindAddress = addressFactory.getBindAddress()
        then: bindAddress == CONFIGURED_BIND_ADDRESS
    }

    def "recognizes configured bind address as local"() {
        def isLocal
        when: isLocal = addressFactory.isLocal(CONFIGURED_BIND_ADDRESS)
        then: isLocal == true
    }

    def "returns configured bind address as local address"() {
        def localAddresses
        when: localAddresses = addressFactory.findLocalAddresses()
        then: localAddresses.contains(CONFIGURED_BIND_ADDRESS)
    }

    def cleanup() {
        System.clearProperty(InetAddressFactory.BIND_ADDRESS_PROPERTY_KEY)
    }
}
