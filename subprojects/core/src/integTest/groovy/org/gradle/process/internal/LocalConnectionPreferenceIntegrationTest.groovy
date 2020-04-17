/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.junit.Assume
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded })
class LocalConnectionPreferenceIntegrationTest extends AbstractIntegrationSpec {
    boolean hasIPv4Available
    boolean hasIPv6Available

    def setup() {
        for (NetworkInterface ni : NetworkInterface.getNetworkInterfaces()) {
            if (ni.isLoopback()) {
                for (InetAddress address : ni.getInetAddresses()) {
                    if (address instanceof Inet6Address) {
                        hasIPv6Available = true
                    } else {
                        hasIPv4Available = true
                    }
                }
            }
        }
        buildFile << """
            def inetAddressFactory = gradle.services.get(${InetAddressFactory.canonicalName})
        """
        executer.requireIsolatedDaemons()
    }

    def "can prefer IPv4 connections"() {
        Assume.assumeTrue("No IPv4 localhost address is available", hasIPv4Available)
        buildFile << """
            assert inetAddressFactory.getLocalBindingAddress() instanceof ${Inet4Address.canonicalName}
        """
        expect:
        executer.withArguments("-D${JvmOptions.JAVA_NET_PREFER_IPV4_KEY}=true")
        succeeds("help")
    }

    def "can prefer IPv6 connections"() {
        Assume.assumeTrue("No IPv6 localhost address is available", hasIPv6Available)
        buildFile << """
            assert inetAddressFactory.getLocalBindingAddress() instanceof ${Inet6Address.canonicalName}
        """
        expect:
        executer.withArguments("-D${JvmOptions.JAVA_NET_PREFER_IPV6_KEY}=true")
        succeeds("help")
    }
}
