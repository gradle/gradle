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

package org.gradle.internal.remote.internal.hub

import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.RejectedMessage
import org.gradle.internal.remote.internal.hub.queue.AbstractQueueTest

class ConnectionSetTest extends AbstractQueueTest {
    final IncomingQueue incomingQueue = new IncomingQueue(lock)
    final OutgoingQueue outgoingQueue = new OutgoingQueue(incomingQueue, lock)
    final ConnectionSet connections = new ConnectionSet(incomingQueue, outgoingQueue)

    def "discards queued outgoing messages when stop requested and no connections"() {
        def channel = new ChannelIdentifier("channel")
        def outgoingMessage = new ChannelMessage(channel, "payload")

        given:
        def incoming = incomingQueue.getChannel(channel).newEndpoint()
        outgoingQueue.dispatch(outgoingMessage)

        when:
        connections.noFurtherConnections()
        def messages = []
        incoming.take(messages)

        then:
        messages.size() == 2
        messages[0] instanceof RejectedMessage
        messages[0].payload == "payload"
        messages[1] instanceof EndOfStream
     }

    def "does not discard queued outgoing messages when stop requested until all connections finished"() {
        def channel = new ChannelIdentifier("channel")
        def message = new ChannelMessage(channel, "payload")
        def sentinel = new ChannelMessage(channel, "payload")

        given:
        def incoming = incomingQueue.getChannel(channel).newEndpoint()
        def connection = connections.add(Mock(RemoteConnection))
        outgoingQueue.dispatch(message)
        incomingQueue.queue(sentinel)

        when:
        connections.noFurtherConnections()
        def messages = []
        incoming.take(messages)

        then:
        messages.size() == 1
        messages == [sentinel]

        when:
        messages = []
        connection.dispatchFinished()
        connection.receiveFinished()
        incoming.take(messages)

        then:
        messages.size() == 2
        messages[0] instanceof RejectedMessage
        messages[0].payload == "payload"
        messages[1] instanceof EndOfStream
    }
}
