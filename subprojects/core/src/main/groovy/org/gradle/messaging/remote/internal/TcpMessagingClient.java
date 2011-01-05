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

import org.gradle.api.Action;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;

import java.net.URI;

/**
 * A {@link org.gradle.messaging.remote.MessagingClient} which uses a single TCP connection with a server.
 */
public class TcpMessagingClient implements MessagingClient {
    private final DefaultMultiChannelConnector connector;
    private final DefaultMessagingClient client;
    private final DefaultExecutorFactory executorFactory;

    public TcpMessagingClient(ClassLoader messagingClassLoader, URI serverAddress) {
        executorFactory = new DefaultExecutorFactory();
        connector = new DefaultMultiChannelConnector(new TcpOutgoingConnector(messagingClassLoader), new NoOpIncomingConnector(), executorFactory);
        client = new DefaultMessagingClient(connector, messagingClassLoader, serverAddress);
    }

    public ObjectConnection getConnection() {
        return client.getConnection();
    }

    public void stop() {
        new CompositeStoppable(client, connector, executorFactory).stop();
    }

    private static class NoOpIncomingConnector implements IncomingConnector {
        public URI accept(Action<ConnectEvent<Connection<Object>>> action) {
            throw new UnsupportedOperationException();
        }
    }
}
