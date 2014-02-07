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

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.OutgoingConnector;

public class MessageHubBackedClient implements MessagingClient {
    private final OutgoingConnector connector;
    private final ExecutorFactory executorFactory;

    public MessageHubBackedClient(OutgoingConnector connector, ExecutorFactory executorFactory) {
        this.connector = connector;
        this.executorFactory = executorFactory;
    }

    public ObjectConnection getConnection(Address address) {
        return new MessageHubBackedObjectConnection(executorFactory, connector.connect(address));
    }
}
