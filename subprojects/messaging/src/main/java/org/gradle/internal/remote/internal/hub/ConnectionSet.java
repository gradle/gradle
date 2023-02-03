/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal.hub;

import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream;
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.internal.remote.internal.hub.queue.EndPointQueue;

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

    /**
     * Adds a new incoming connection.
     */
    public ConnectionState add(RemoteConnection<InterHubMessage> connection) {
        EndPointQueue queue = outgoingQueue.newEndpoint();
        ConnectionState state = new ConnectionState(this, connection, queue);
        connections.add(state);
        return state;
    }

    /**
     * Called when all dispatch and receive has completed on the given connection.
     */
    public void finished(ConnectionState connectionState) {
        connections.remove(connectionState);
        if (stopping) {
            maybeStop();
        }
    }

    /**
     * Called when no further incoming connections will be added.
     */
    public void noFurtherConnections() {
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
