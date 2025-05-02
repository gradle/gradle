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

package org.gradle.internal.remote.internal.inet

import spock.lang.Specification

class InetAddressFactoryTest extends Specification {

    def factory = new InetAddressFactory()
    InetAddresses addresses = Mock()

    def setup() {
        factory.inetAddresses = addresses
    }

    def "communication addresses are detected"() {
        when:
        loopbackAddresses([ip(127, 0, 0, 1), ip(127, 0, 0, 2)])

        then:
        factory.isCommunicationAddress(factory.getLocalBindingAddress())

        !factory.isCommunicationAddress(ip(127, 0, 0, 3))
    }

    def "wildcard address is always present"() {

        when:
        defaultAddresses()

        then:
        factory.wildcardBindingAddress == new InetSocketAddress(0).address
    }

    def "loopback is used as bind address if available"() {
        when:
        defaultAddresses()

        then:
        factory.localBindingAddress == ip(127, 0, 0, 1)
    }

    def "wildcard address is used as bind address if no loopback available"() {
        when:
        loopbackAddresses([])
        remoteAddresses([ip(192, 168, 18, 256)])

        then:
        factory.localBindingAddress == new InetSocketAddress(0).address
    }

    def "Always returns some communication address"() {
        expect:
        new InetAddressFactory().localBindingAddress
    }

    private defaultAddresses() {
        loopbackAddresses([ip(127, 0, 0, 1)])
    }

    InetAddress ip(int a, int b, int c, int d) {
        InetAddress.getByAddress([a, b, c, d] as byte[])
    }

    private void loopbackAddresses(List<InetAddress> loopback) {
        addresses.getLoopback() >> loopback
    }

    private void remoteAddresses(List<InetAddress> remote) {
        addresses.getRemote() >> remote
    }
}
