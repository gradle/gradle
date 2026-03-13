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

    Map<String, String> environment = [:]
    InetAddressFactory factory
    InetAddresses addresses = Mock()

    def setup() {
        factory = new InetAddressFactory(addresses) {
            @Override
            String getEnv(String name) {
                return environment.get(name)
            }

            @Override
            Set<String> getEnvKeys() {
                return environment.keySet()
            }
        }
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

    def "OPENSHIFT address overrides the default local binding address"() {
        when:
        defaultAddresses()
        environment["OPENSHIFT_FOO_IP"] = "10.0.0.1"

        then:
        factory.localBindingAddress == InetAddress.getByName("10.0.0.1")
    }

    def "invalid OPENSHIFT address throws a descriptive error"() {
        when:
        defaultAddresses()
        environment["OPENSHIFT_FOO_IP"] = "256.0.0.1"
        factory.localBindingAddress

        then:
        def e = thrown(RuntimeException)
        e.cause.message.contains("OPENSHIFT_FOO_IP")
        e.cause.message.contains("256.0.0.1")
    }

    def "GRADLE_DAEMON_BIND_ADDRESS skips auto-detection and uses the provided address"() {
        when:
        environment["GRADLE_DAEMON_BIND_ADDRESS"] = "192.168.1.10"

        then:
        factory.localBindingAddress == InetAddress.getByName("192.168.1.10")
        0 * addresses._
    }

    def "GRADLE_DAEMON_BIND_ADDRESS takes precedence over OPENSHIFT address"() {
        when:
        environment["OPENSHIFT_FOO_IP"] = "10.0.0.1"
        environment["GRADLE_DAEMON_BIND_ADDRESS"] = "192.168.1.10"

        then:
        factory.localBindingAddress == InetAddress.getByName("192.168.1.10")
        0 * addresses._
    }

    def "invalid GRADLE_DAEMON_BIND_ADDRESS throws a descriptive error"() {
        when:
        environment["GRADLE_DAEMON_BIND_ADDRESS"] = "256.0.0.1"
        factory.localBindingAddress

        then:
        def e = thrown(RuntimeException)
        e.cause.message.contains("GRADLE_DAEMON_BIND_ADDRESS")
        e.cause.message.contains("256.0.0.1")
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
