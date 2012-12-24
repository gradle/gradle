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
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Stoppable;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultMessagingServer implements MessagingServer, Stoppable {
    private final MultiChannelConnector connector;
    private final Set<ObjectConnection> connections = new CopyOnWriteArraySet<ObjectConnection>();

    public DefaultMessagingServer(MultiChannelConnector connector) {
        this.connector = connector;
    }

    public Address accept(final Action<ConnectEvent<ObjectConnection>> action) {
        return connector.accept(new Action<ConnectEvent<MultiChannelConnection<Object>>>() {
            public void execute(ConnectEvent<MultiChannelConnection<Object>> connectEvent) {
                finishConnect(connectEvent, action);
            }
        });
    }

    private void finishConnect(ConnectEvent<MultiChannelConnection<Object>> connectEvent,
                               Action<ConnectEvent<ObjectConnection>> action) {
        MultiChannelConnection<Object> messageConnection = connectEvent.getConnection();
        IncomingMethodInvocationHandler incoming = new IncomingMethodInvocationHandler(messageConnection);
        OutgoingMethodInvocationHandler outgoing = new OutgoingMethodInvocationHandler(messageConnection);
        AtomicReference<ObjectConnection> connectionRef = new AtomicReference<ObjectConnection>();
        AsyncStoppable stopControl = new ConnectionAsyncStoppable(messageConnection, connectionRef);

        DefaultObjectConnection connection = new DefaultObjectConnection(stopControl, outgoing, incoming);
        connectionRef.set(connection);
        connections.add(connection);
        action.execute(new ConnectEvent<ObjectConnection>(connection, connectEvent.getLocalAddress(), connectEvent.getRemoteAddress()));
    }

    public void stop() {
        for (ObjectConnection connection : connections) {
            connection.requestStop();
        }
        try {
            CompositeStoppable.stoppable(connections).stop();
        } finally {
            connections.clear();
        }
    }

    private class ConnectionAsyncStoppable implements AsyncStoppable {
        private final MultiChannelConnection<Object> messageConnection;
        private final AtomicReference<ObjectConnection> connectionRef;

        public ConnectionAsyncStoppable(MultiChannelConnection<Object> messageConnection,
                                        AtomicReference<ObjectConnection> connectionRef) {
            this.messageConnection = messageConnection;
            this.connectionRef = connectionRef;
        }

        public void requestStop() {
            messageConnection.requestStop();
        }

        public void stop() {
            try {
                messageConnection.stop();
            } finally {
                connections.remove(connectionRef.get());
            }
        }
    }
}
