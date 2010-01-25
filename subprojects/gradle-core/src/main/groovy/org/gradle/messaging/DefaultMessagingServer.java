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
package org.gradle.messaging;

import org.gradle.messaging.dispatch.*;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultMessagingServer implements MessagingServer {
    private final Connector connector;
    private final ClassLoader classLoader;
    private final Set<ObjectConnection> connections = new CopyOnWriteArraySet<ObjectConnection>();

    public DefaultMessagingServer(Connector connector, ClassLoader classLoader) {
        this.connector = connector;
        this.classLoader = classLoader;
    }

    public ObjectConnection createUnicastConnection() {
        IncomingMethodInvocationHandler incoming = new IncomingMethodInvocationHandler(classLoader);
        final OutgoingConnection<Message> messageConnection = connector.accept(incoming.getIncomingDispatch());
        OutgoingMethodInvocationHandler outgoing = new OutgoingMethodInvocationHandler(messageConnection);
        final AtomicReference<ObjectConnection> connectionRef = new AtomicReference<ObjectConnection>();
        AsyncStoppable stopControl = new AsyncStoppable() {
            public void requestStop() {
                messageConnection.requestStop();
            }

            public void stop() {
                messageConnection.stop();
                connections.remove(connectionRef.get());
            }
        };

        DefaultObjectConnection connection = new DefaultObjectConnection(messageConnection, stopControl, outgoing,
                incoming);
        connectionRef.set(connection);
        connections.add(connection);
        return connection;
    }

    public void stop() {
        for (ObjectConnection connection : connections) {
            connection.requestStop();
        }
        for (ObjectConnection connection : connections) {
            connection.stop();
        }
    }
}
