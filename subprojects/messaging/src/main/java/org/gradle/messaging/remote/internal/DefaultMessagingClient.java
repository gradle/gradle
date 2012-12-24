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

import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Stoppable;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;

import java.util.HashSet;
import java.util.Set;

public class DefaultMessagingClient implements MessagingClient, Stoppable {
    private final Set<ObjectConnection> connections = new HashSet<ObjectConnection>();
    private final MultiChannelConnector connector;

    public DefaultMessagingClient(MultiChannelConnector connector) {
        this.connector = connector;
    }

    public ObjectConnection getConnection(Address address) {
        MultiChannelConnection<Object> connection = connector.connect(address);
        IncomingMethodInvocationHandler incoming = new IncomingMethodInvocationHandler(connection);
        OutgoingMethodInvocationHandler outgoing = new OutgoingMethodInvocationHandler(connection);
        ObjectConnection objectConnection = new DefaultObjectConnection(connection, outgoing, incoming);
        connections.add(objectConnection);
        return objectConnection;
    }

    public void stop() {
        CompositeStoppable.stoppable(connections).stop();
    }
}
