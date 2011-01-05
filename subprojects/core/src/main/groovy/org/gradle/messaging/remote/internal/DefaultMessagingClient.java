/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;

import java.net.URI;

public class DefaultMessagingClient implements MessagingClient {
    private final ObjectConnection connection;

    public DefaultMessagingClient(MultiChannelConnector connector, ClassLoader classLoader, URI serverAddress) {
        MultiChannelConnection<Object> connection = connector.connect(serverAddress);
        IncomingMethodInvocationHandler incoming = new IncomingMethodInvocationHandler(classLoader, connection);
        OutgoingMethodInvocationHandler outgoing = new OutgoingMethodInvocationHandler(connection);
        this.connection = new DefaultObjectConnection(connection, connection, outgoing, incoming);
    }

    public ObjectConnection getConnection() {
        return connection;
    }

    public void stop() {
        connection.stop();
    }
}
