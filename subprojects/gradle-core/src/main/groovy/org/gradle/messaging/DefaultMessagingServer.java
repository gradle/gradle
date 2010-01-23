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

import org.gradle.api.Action;
import org.gradle.messaging.dispatch.Connector;
import org.gradle.messaging.dispatch.OutgoingConnection;
import org.gradle.messaging.dispatch.Message;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultMessagingServer implements MessagingServer {
    private final Connector connector;
    private final ClassLoader classLoader;
    private final Set<ObjectConnection> connections = new CopyOnWriteArraySet<ObjectConnection>();
    private final Action<ObjectConnection> cleanupAction = new Action<ObjectConnection>() {
        public void execute(ObjectConnection objectConnection) {
            connections.remove(objectConnection);
        }
    };

    public DefaultMessagingServer(Connector connector, ClassLoader classLoader) {
        this.connector = connector;
        this.classLoader = classLoader;
    }

    public ObjectConnection createUnicastConnection() {
        IncomingMethodInvocationHandler incoming = new IncomingMethodInvocationHandler(classLoader);
        OutgoingConnection<Message> messageConnection = connector.accept(incoming.getIncomingDispatch());
        OutgoingMethodInvocationHandler outgoing = new OutgoingMethodInvocationHandler(messageConnection);
        DefaultObjectConnection connection = new DefaultObjectConnection(messageConnection, outgoing, incoming,
                cleanupAction);
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
