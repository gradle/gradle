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
package org.gradle.messaging.remote.internal.protocol

import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress
import org.gradle.internal.id.UUIDGenerator
import spock.lang.Shared
import spock.lang.Specification
import org.gradle.messaging.remote.internal.MessageOriginator
import org.gradle.messaging.remote.internal.inet.SocketInetAddress

class DiscoveryProcotolSerializerTest extends Specification {
    final DiscoveryProtocolSerializer serializer = new DiscoveryProtocolSerializer()
    @Shared def uuidGenerator = new UUIDGenerator()
    @Shared MessageOriginator messageOriginator = new MessageOriginator(uuidGenerator.generateId(), "source display name")
    @Shared InetAddress address = InetAddress.getByName(null)
    final InetAddress receivedAddress = Mock()

    def "writes and reads message types"() {
        when:
        def result = send(original)

        then:
        result == original

        where:
        original << [
                new LookupRequest(messageOriginator, "group", "channel"),
                new ChannelUnavailable(messageOriginator, "group", "channel", new MultiChoiceAddress(UUID.randomUUID(), 8091, [address]))
        ]
    }

    def "mixes in remote address to received ChannelAvailable message"() {
        def originatorId = UUID.randomUUID()
        def original = new ChannelAvailable(messageOriginator, "group", "channel", new MultiChoiceAddress(originatorId, 8091, [address]))
        def expected = new ChannelAvailable(messageOriginator, "group", "channel", new MultiChoiceAddress(originatorId, 8091, [receivedAddress, address]))

        when:
        def result = send(original)

        then:
        result == expected
    }

    def "can read message for unknown protocol version"() {
        expect:
        def result = send { outstr ->
            outstr.writeByte(90)
        }
        result instanceof UnknownMessage
        result.toString() == "unknown protocol version 90"
    }

    def "can read unknown message type"() {
        expect:
        def result = send { outstr ->
            outstr.writeByte(DiscoveryProtocolSerializer.PROTOCOL_VERSION);
            outstr.writeByte(90)
        }
        result instanceof UnknownMessage
        result.toString() == "unknown message type 90"
    }

    def send(Closure cl) {
        def bytesOut = new ByteArrayOutputStream()
        def outstr = new DataOutputStream(bytesOut)
        cl.call(outstr)
        outstr.close()

        def bytesIn = new ByteArrayInputStream(bytesOut.toByteArray())
        return serializer.newReader(new DataInputStream(bytesIn), null, new SocketInetAddress(receivedAddress, 9122)).read()
    }

    def send(DiscoveryMessage message) {
        def bytesOut = new ByteArrayOutputStream()
        def outstr = new DataOutputStream(bytesOut)
        serializer.newWriter(outstr).write(message)
        outstr.close()

        def bytesIn = new ByteArrayInputStream(bytesOut.toByteArray())
        return serializer.newReader(new DataInputStream(bytesIn), null, new SocketInetAddress(receivedAddress, 9122)).read()
    }
}
