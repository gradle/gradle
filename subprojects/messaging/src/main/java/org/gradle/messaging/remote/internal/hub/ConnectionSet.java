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

import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionClosed;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionEstablished;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.hub.queue.EndPointQueue;

import java.util.HashSet;
import java.util.Set;

class ConnectionSet {
    private final Set<ConnectionState> connections = new HashSet<ConnectionState>();
    private final IncomingQueue incomingQueue;
    private final OutgoingQueue outgoingQueue;
    private boolean stopping;

    ConnectionSet(IncomingQueue incomingQueue, OutgoingQueue outgoingQueue) {
        this.incomingQueue = incomingQueue;
        this.outgoingQueue = outgoingQueue;
    }

    public ConnectionState add(Connection<InterHubMessage> connection) {
        incomingQueue.queue(new ConnectionEstablished(connection));
        EndPointQueue queue = outgoingQueue.newEndpoint();
        ConnectionState state = new ConnectionState(this, connection, queue);
        connections.add(state);
        return state;
    }

    public void finished(ConnectionState connectionState) {
        incomingQueue.queue(new ConnectionClosed(connectionState.getConnection()));
        connections.remove(connectionState);
        if (stopping) {
            maybeStop();
        }
    }

    public void requestStop() {
        stopping = true;
        maybeStop();
    }

    private void maybeStop() {
        if (connections.isEmpty()) {
            outgoingQueue.discardQueued();
            incomingQueue.queue(new EndOfStream());
        }
    }
}
