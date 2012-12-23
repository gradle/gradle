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
        serialized.length == 13
    }

    def "replaces a channel ID that has already been seen with an integer value"() {
        def channelId = new ChannelIdentifier("name")
        def message1 = new ChannelMessage(channelId, "payload 1")
        def message2 = new ChannelMessage(channelId, "payload 2")

        when:
        def serialized = serialize(message1, message2)
        def result = deserializeMultiple(serialized, 2)

        then:
        result[0] instanceof ChannelMessage
        result[0].channel == channelId
        result[0].payload == "payload 1"
        result[1] instanceof ChannelMessage
        result[1].channel == channelId
        result[1].payload == "payload 2"
        serialized.length == 26
    }

    def "can serialize messages for multiple channels"() {
        def channelId1 = new ChannelIdentifier("channel 1")
        def channelId2 = new ChannelIdentifier("channel 2")
        def message1 = new ChannelMessage(channelId1, "payload 1") // 20
        def message2 = new ChannelMessage(channelId2, "payload 2") // 20
        def message3 = new ChannelMessage(channelId1, "payload 3") // 11

        when:
        def serialized = serialize(message1, message2, message3)
        def result = deserializeMultiple(serialized, 3)

        then:
        result[0] instanceof ChannelMessage
        result[0].channel == channelId1
        result[0].payload == "payload 1"
        result[1] instanceof ChannelMessage
        result[1].channel == channelId2
        result[1].payload == "payload 2"
        result[2] instanceof ChannelMessage
        result[2].channel == channelId1
        result[2].payload == "payload 3"
        serialized.length == 51
    }

    def "can serialise EndOfStream"() {
        when:
        def serialized = serialize(new EndOfStream())
        def result = deserialize(serialized)

        then:
        result instanceof EndOfStream
        serialized.length == 1
    }

    def serialize(InterHubMessage... messages) {
        def outStr = new ByteArrayOutputStream()
        def dataStr = new DataOutputStream(outStr)
        def writer = serializer.newWriter(dataStr)
        messages.each {
            writer.write(it)
        }
        dataStr.flush()
        return outStr.toByteArray()
    }

    def deserialize(byte[] data) {
        return serializer.newReader(new ByteArrayInputStream(data), null, null).read()
    }

    def deserializeMultiple(byte[] data, int count) {
        def reader = serializer.newReader(new ByteArrayInputStream(data), null, null)
        def result = []
        count.times {
            result << reader.read()
        }
        return result
    }
}
