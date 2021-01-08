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

import org.gradle.api.Action
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.ObjectConnection
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.IncomingConnector
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import spock.lang.Specification

class MessageHubBackedServerTest extends Specification {
    final IncomingConnector connector = Mock()
    final ExecutorFactory executorFactory = Mock()
    final MessageHubBackedServer server = new MessageHubBackedServer(connector, executorFactory)

    def "creates connection and cleans up on stop"() {
        ConnectionAcceptor acceptor = Mock()
        Action<ObjectConnection> connectAction = Mock()
        RemoteConnection<InterHubMessage> backingConnection = Mock()
        ManagedExecutor executor = Mock()
        ConnectCompletion completion = Mock()
        Action<ConnectCompletion> acceptAction
        def connection

        when:
        server.accept(connectAction)

        then:
        1 * connector.accept(_, false) >> { acceptAction = it[0]; return acceptor }

        when:
        acceptAction.execute(completion)

        then:
        1 * executorFactory.create("${completion} workers") >> executor
        1 * connectAction.execute(_) >> { ObjectConnection c -> connection = c }

        when:
        connection.connect()

        then:
        1 * completion.create(_) >> backingConnection

        when:
        connection.stop()

        then:
        1 * backingConnection.stop()
        1 * executor.stop()
        0 * _._
    }
}
