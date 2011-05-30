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
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.util.IdGenerator;
import org.gradle.util.UUIDGenerator;

import java.util.UUID;

public class DefaultMultiChannelConnector implements MultiChannelConnector, Stoppable {
    private final OutgoingConnector<Message> outgoingConnector;
    private final ExecutorFactory executorFactory;
    private final StoppableExecutor executorService;
    private final HandshakeIncomingConnector incomingConnector;
    private final IdGenerator<UUID> idGenerator = new UUIDGenerator();
    private final ClassLoader messagingClassLoader;

    public DefaultMultiChannelConnector(OutgoingConnector<Message> outgoingConnector, IncomingConnector<Message> incomingConnector,
                                        ExecutorFactory executorFactory, ClassLoader messagingClassLoader) {
        this.messagingClassLoader = messagingClassLoader;
        this.outgoingConnector = new HandshakeOutgoingConnector(outgoingConnector);
        this.executorFactory = executorFactory;
        executorService = executorFactory.create("Incoming Connection Handler");
        this.incomingConnector = new HandshakeIncomingConnector(incomingConnector, executorService);
    }

    public void stop() {
        executorService.stop();
    }

    public Address accept(final Action<ConnectEvent<MultiChannelConnection<Object>>> action) {
        Action<ConnectEvent<Connection<Message>>> connectAction = new Action<ConnectEvent<Connection<Message>>>() {
            public void execute(ConnectEvent<Connection<Message>> event) {
                finishConnect(event, action);
            }
        };
        return incomingConnector.accept(connectAction, false);
    }

    private void finishConnect(ConnectEvent<Connection<Message>> event,
                               Action<ConnectEvent<MultiChannelConnection<Object>>> action) {
        Address localAddress = event.getLocalAddress();
        Address remoteAddress = event.getRemoteAddress();
        MessageHub hub = new MessageHub(String.format("Incoming Connection %s", localAddress), "message server", executorFactory, idGenerator, messagingClassLoader);
        DefaultMultiChannelConnection channelConnection = new DefaultMultiChannelConnection(hub, event.getConnection(), localAddress, remoteAddress);
        action.execute(new ConnectEvent<MultiChannelConnection<Object>>(channelConnection, localAddress, remoteAddress));
    }

    public MultiChannelConnection<Object> connect(Address destinationAddress) {
        Connection<Message> connection = outgoingConnector.connect(destinationAddress);
        MessageHub hub = new MessageHub(String.format("Outgoing Connection %s", destinationAddress), "message client", executorFactory, idGenerator, messagingClassLoader);
        return new DefaultMultiChannelConnection(hub, connection, null, destinationAddress);
    }
}
