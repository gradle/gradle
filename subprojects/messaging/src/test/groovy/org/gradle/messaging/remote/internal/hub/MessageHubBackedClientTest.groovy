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

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.MessageSerializer
import org.gradle.messaging.remote.internal.OutgoingConnector
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import spock.lang.Specification

class MessageHubBackedClientTest extends Specification {
    final OutgoingConnector connector = Mock()
    final ExecutorFactory executorFactory = Mock()
    final MessageSerializer<InterHubMessage> serializer = Mock()
    final MessageHubBackedClient client = new MessageHubBackedClient(connector, serializer, executorFactory)

    def "creates connection and cleans up on stop"() {
        Address address = Stub()
        Connection<InterHubMessage> backingConnection = Mock()
        StoppableExecutor executor = Mock()

        when:
        def objectConnection = client.getConnection(address)

        then:
        1 * connector.connect(address, serializer) >> backingConnection
        1 * executorFactory.create("${backingConnection} workers") >> executor

        when:
        objectConnection.stop()

        then:
        1 * backingConnection.stop()
        1 * executor.stop()
        0 * _._
    }
}
