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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.IncomingConnector;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHubBackedServer implements MessagingServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHubBackedServer.class);
    private final IncomingConnector connector;
    private final MessageSerializer<InterHubMessage> serializer;
    private final ExecutorFactory executorFactory;

    public MessageHubBackedServer(IncomingConnector connector, MessageSerializer<InterHubMessage> serializer, ExecutorFactory executorFactory) {
        this.connector = connector;
        this.serializer = serializer;
        this.executorFactory = executorFactory;
    }

    public Address accept(Action<ConnectEvent<ObjectConnection>> action) {
        return connector.accept(new ConnectEventAction(action), serializer, false);
    }

    private class ConnectEventAction implements Action<ConnectEvent<Connection<InterHubMessage>>> {
        private final Action<ConnectEvent<ObjectConnection>> action;

        public ConnectEventAction(Action<ConnectEvent<ObjectConnection>> action) {
            this.action = action;
        }

        public void execute(ConnectEvent<Connection<InterHubMessage>> connectEvent) {
            Connection<InterHubMessage> connection = connectEvent.getConnection();
            MessageHub hub = new MessageHub(connection.toString(), executorFactory, new Action<Throwable>() {
                public void execute(Throwable throwable) {
                    LOGGER.error("Unexpected exception thrown.", throwable);
                }
            });
            MessageHubBackedObjectConnection objectConnection = new MessageHubBackedObjectConnection(hub, connection);
            action.execute(new ConnectEvent<ObjectConnection>(objectConnection, connectEvent.getLocalAddress(), connectEvent.getRemoteAddress()));
        }
    }
}
