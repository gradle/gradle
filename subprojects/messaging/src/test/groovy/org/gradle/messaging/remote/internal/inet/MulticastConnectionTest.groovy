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

import org.gradle.messaging.remote.internal.DefaultMessageSerializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import org.junit.internal.AssumptionViolatedException
import spock.lang.Timeout

@Timeout(30)
class MulticastConnectionTest extends ConcurrentSpec {
    def addressFactory = new InetAddressFactory()
    @Rule ReleasingPortAllocator portFinder = new ReleasingPortAllocator()

    def "can send multicast messages between peers"() {
        def port = portFinder.assignPort()
        def address = new SocketInetAddress(InetAddress.getByName("233.253.17.122"), port)
        def serializer = new DefaultMessageSerializer<String>(getClass().classLoader)

        given:
        multicastAvailable(address)

        when:
        def connection1 = new MulticastConnection<String>(address, serializer, addressFactory)
        def connection2 = new MulticastConnection<String>(address, serializer, addressFactory)
        connection1.dispatch("hi!")

        then:
        connection2.receive() == "hi!"

        cleanup:
        connection1?.stop()
        connection2?.stop()
    }

    void multicastAvailable(SocketInetAddress address) {
        def socket1 = new MulticastSocket(address.port);
        def inetAddress = new InetSocketAddress(address.address, address.port)
        addressFactory.findMulticastInterfaces().each { networkInterface ->
            try {
                socket1.joinGroup(inetAddress, networkInterface);
            } catch (SocketException e) {
                // Ignore
            }
        }
        def socket2 = new MulticastSocket(address.port);
        addressFactory.findMulticastInterfaces().each { networkInterface ->
            try {
                socket2.joinGroup(inetAddress, networkInterface);
            } catch (SocketException e) {
                // Ignore
            }
        }

        def message = "hi".getBytes()
        socket1.send(new DatagramPacket(message, message.length, address.address, address.port))

        socket2.setSoTimeout(10000);

        def packet = new DatagramPacket(new byte[1024], 1024)
        try {
            socket2.receive(packet)
        } catch (SocketTimeoutException e) {
            throw new AssumptionViolatedException("""Timeout waiting to receive message.
network interfaces: ${NetworkInterface.networkInterfaces.collect { it.displayName }.join(', ')}
selected: ${addressFactory.findMulticastInterfaces().collect { it.displayName }.join(', ')}
""", e)
        }

        socket1.close()
        socket2.close()
        assert new String(packet.data, packet.offset, packet.length) == "hi"
    }
}
