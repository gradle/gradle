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

import org.gradle.api.Action;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.protocol.ConnectRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class HandshakeIncomingConnector implements IncomingConnector<Message> {
    private final IncomingConnector<Message> connector;
    private final Executor executor;
    private final Object lock = new Object();
    private Address localAddress;
    private long nextId;
    private final Map<Address, Action<ConnectEvent<Connection<Message>>>> pendingActions = new HashMap<Address, Action<ConnectEvent<Connection<Message>>>>();

    public HandshakeIncomingConnector(IncomingConnector<Message> connector, Executor executor) {
        this.connector = connector;
        this.executor = executor;
    }

    public Address accept(Action<ConnectEvent<Connection<Message>>> action, boolean allowRemote) {
        assert !allowRemote;
        synchronized (lock) {
            if (localAddress == null) {
                localAddress = connector.accept(handShakeAction(), false);
            }

            Address localAddress = new CompositeAddress(this.localAddress, nextId++);
            pendingActions.put(localAddress, action);
            return localAddress;
        }
    }

    private Action<ConnectEvent<Connection<Message>>> handShakeAction() {
        return new Action<ConnectEvent<Connection<Message>>>() {
            public void execute(final ConnectEvent<Connection<Message>> connectEvent) {
                executor.execute(new Runnable() {
                    public void run() {
                        handshake(connectEvent);
                    }
                });
            }
        };
    }

    private void handshake(ConnectEvent<Connection<Message>> connectEvent) {
        Connection<Message> connection = connectEvent.getConnection();
        ConnectRequest request = (ConnectRequest) connection.receive();
        Address localAddress = request.getDestinationAddress();
        Action<ConnectEvent<Connection<Message>>> channelConnection;
        synchronized (lock) {
            channelConnection = pendingActions.remove(localAddress);
        }
        if (channelConnection == null) {
            throw new IllegalStateException(String.format(
                    "Request to connect received for unknown address '%s'.", localAddress));
        }
        channelConnection.execute(new ConnectEvent<Connection<Message>>(connection, localAddress, connectEvent.getRemoteAddress()));
    }
}
