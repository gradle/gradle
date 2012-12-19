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

import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionClosed;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionEstablished;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.remote.internal.hub.queue.EndPointQueue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

class ConnectionSet {
    private final Set<ConnectionState> connections = new HashSet<ConnectionState>();
    private final Condition condition;
    private final IncomingQueue incomingQueue;
    private final OutgoingQueue outgoingQueue;

    ConnectionSet(Lock lock, IncomingQueue incomingQueue, OutgoingQueue outgoingQueue) {
        this.incomingQueue = incomingQueue;
        this.outgoingQueue = outgoingQueue;
        this.condition = lock.newCondition();
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
        condition.signalAll();
    }

    public void waitForConnectionsToComplete() {
        while (!connections.isEmpty()) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        incomingQueue.queue(new EndOfStream());
    }
}
