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

package org.gradle.internal.remote.internal.hub.queue

import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream

class MultiChannelQueueTest extends AbstractQueueTest {
    final MultiChannelQueue queue = new MultiChannelQueue(lock)

    def "adds and caches channel queue on first lookup"() {
        def id1 = new ChannelIdentifier("channel1")
        def id2 = new ChannelIdentifier("channel2")

        expect:
        def channelQueue = queue.getChannel(id1)
        channelQueue == queue.getChannel(id1)
        channelQueue != queue.getChannel(id2)
    }

    def "adds channel queue when channel message added"() {
        def id1 = new ChannelIdentifier("channel1")
        def id2 = new ChannelIdentifier("channel2")
        def message1 = new ChannelMessage(id1, "message 1")
        def message2 = new ChannelMessage(id2, "message 2")
        def message3 = new ChannelMessage(id1, "message 3")

        given:
        queue.queue(message1)
        queue.queue(message2)
        queue.queue(message3)

        when:
        def messages1 = []
        def messages2 = []
        queue.getChannel(id1).newEndpoint().take(messages1)
        queue.getChannel(id2).newEndpoint().take(messages2)

        then:
        messages1 == [message1, message3]
        messages2 == [message2]
    }

    def "forwards channel message to channel queue"() {
        def id1 = new ChannelIdentifier("channel1")
        def id2 = new ChannelIdentifier("channel2")
        def message1 = new ChannelMessage(id1, "message 1")
        def message2 = new ChannelMessage(id2, "message 2")
        def message3 = new ChannelMessage(id1, "message 3")

        given:
        def endpoint1 = queue.getChannel(id1).newEndpoint()
        def endpoint2 = queue.getChannel(id2).newEndpoint()

        when:
        def messages1 = []
        def messages2 = []
        queue.queue(message1)
        queue.queue(message2)
        queue.queue(message3)
        endpoint1.take(messages1)
        endpoint2.take(messages2)

        then:
        messages1 == [message1, message3]
        messages2 == [message2]
    }

    def "forwards broadcast message to all channel queues"() {
        def id1 = new ChannelIdentifier("channel1")
        def id2 = new ChannelIdentifier("channel2")
        def message1 = new ChannelMessage(id1, "message 1")
        def message2 = broadcast()

        given:
        def endpoint1 = queue.getChannel(id1).newEndpoint()
        def endpoint2 = queue.getChannel(id2).newEndpoint()

        when:
        def messages1 = []
        def messages2 = []
        queue.queue(message1)
        queue.queue(message2)
        endpoint1.take(messages1)
        endpoint2.take(messages2)

        then:
        messages1 == [message1, message2]
        messages2 == [message2]
    }

    def "forwards most recent stateful broadcast messages to all new queues"() {
        def id1 = new ChannelIdentifier("channel1")
        def message1 = broadcast()
        def message2 = broadcast()
        def message3 = new EndOfStream()

        given:
        queue.queue(message1)
        queue.queue(message2)
        queue.queue(message3)

        when:
        def endpoint1 = queue.getChannel(id1).newEndpoint()
        def endpoint2 = queue.getChannel(id1).newEndpoint()
        def messages1 = []
        def messages2 = []
        endpoint1.take(messages1)
        endpoint2.take(messages2)

        then:
        messages1 == [message3]
        messages2 == [message3]
    }
}
