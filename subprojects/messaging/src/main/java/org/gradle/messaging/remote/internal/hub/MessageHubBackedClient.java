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
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.OutgoingConnector;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHubBackedClient implements MessagingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHubBackedClient.class);
    private final OutgoingConnector<InterHubMessage> connector;
    private final ExecutorFactory executorFactory;

    public MessageHubBackedClient(OutgoingConnector<InterHubMessage> connector, ExecutorFactory executorFactory) {
        this.connector = connector;
        this.executorFactory = executorFactory;
    }

    public ObjectConnection getConnection(Address address) {
        Connection<InterHubMessage> connection = connector.connect(address);
        MessageHub hub = new MessageHub(connection.toString(), executorFactory, new Action<Throwable>() {
            public void execute(Throwable throwable) {
                LOGGER.error("Unexpected exception thrown.", throwable);
            }
        });
        return new MessageHubBackedObjectConnection(hub, connection);
    }
}
