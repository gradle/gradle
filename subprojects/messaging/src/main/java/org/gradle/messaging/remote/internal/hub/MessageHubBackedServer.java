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
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.ObjectConnectionCompletion;
import org.gradle.messaging.remote.internal.ConnectCompletion;
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

    public ConnectionAcceptor accept(Action<ObjectConnectionCompletion> action) {
        return connector.accept(new ConnectEventAction(action), false);
    }

    private class ConnectEventAction implements Action<ConnectCompletion> {
        private final Action<ObjectConnectionCompletion> action;

        public ConnectEventAction(Action<ObjectConnectionCompletion> action) {
            this.action = action;
        }

        public void execute(ConnectCompletion completion) {
            Connection<InterHubMessage> connection = completion.create(serializer);
            MessageHub hub = new MessageHub(connection.toString(), executorFactory, new Action<Throwable>() {
                public void execute(Throwable throwable) {
                    LOGGER.error("Unexpected exception thrown.", throwable);
                }
            });
            final MessageHubBackedObjectConnection objectConnection = new MessageHubBackedObjectConnection(hub, connection);
            action.execute(new ObjectConnectionCompletion() {
                public ObjectConnection create() {
                    return objectConnection;
                }
            });
        }
    }
}
