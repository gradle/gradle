/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub

import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import spock.lang.Specification

class InterHubMessageSerializerTest extends Specification {
    final InterHubMessageSerializer serializer = new InterHubMessageSerializer()

    def "can serialise ChannelMessage"() {
        def channelId = new ChannelIdentifier("name")
        def message = new ChannelMessage(channelId, "payload")

        when:
        def serialized = serialize(message)
        def result = deserialize(serialized)

        then:
        result instanceof ChannelMessage
        result.channel == channelId
        result.payload == "payload"
        serialized.length == 16
    }

    def "can serialise EndOfStream"() {
        when:
        def serialized = serialize(new EndOfStream())
        def result = deserialize(serialized)

        then:
        result instanceof EndOfStream
        serialized.length == 1
    }

    def serialize(InterHubMessage message) {
        def outStr = new ByteArrayOutputStream()
        def dataStr = new DataOutputStream(outStr)
        serializer.write(message, dataStr)
        dataStr.flush()
        return outStr.toByteArray()
    }

    def deserialize(byte[] data) {
        return serializer.read(new DataInputStream(new ByteArrayInputStream(data)), null, null)
    }
}
