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
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;

import java.net.URI;

/**
 * A {@link org.gradle.messaging.remote.MessagingServer} implementation which uses a single incoming TCP port for all peers.
 */
public class TcpMessagingServer implements MessagingServer {
    private final TcpIncomingConnector incomingConnector;
    private final DefaultMultiChannelConnector connector;
    private final DefaultMessagingServer server;
    private final DefaultExecutorFactory executorFactory;

    public TcpMessagingServer(ClassLoader messageClassLoader) {
        executorFactory = new DefaultExecutorFactory();
        incomingConnector = new TcpIncomingConnector(executorFactory, messageClassLoader);
        connector = new DefaultMultiChannelConnector(new NoOpOutgoingConnector(), incomingConnector, executorFactory);
        server = new DefaultMessagingServer(connector, messageClassLoader);
    }

    public URI accept(Action<ConnectEvent<ObjectConnection>> action) {
        return server.accept(action);
    }

    public void stop() {
        incomingConnector.requestStop();
        new CompositeStoppable(server, connector, incomingConnector, executorFactory).stop();
    }

    private static class NoOpOutgoingConnector implements OutgoingConnector {
        public Connection<Message> connect(URI destinationUri) {
            throw new UnsupportedOperationException();
        }
    }
}
