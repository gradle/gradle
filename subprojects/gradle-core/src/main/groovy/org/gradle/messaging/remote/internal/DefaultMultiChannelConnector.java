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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.util.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultMultiChannelConnector implements MultiChannelConnector, Stoppable {
    private final OutgoingConnector outgoingConnector;
    private final IncomingConnector incomingConnector;
    private final ExecutorFactory executorFactory;
    private final Lock lock = new ReentrantLock();
    private int nextConnectionId;
    private final Map<URI, DefaultMultiChannelConnection> pending = new HashMap<URI, DefaultMultiChannelConnection>();
    private final StoppableExecutor executorService;

    public DefaultMultiChannelConnector(OutgoingConnector outgoingConnector, IncomingConnector incomingConnector, ExecutorFactory executorFactory) {
        this.outgoingConnector = outgoingConnector;
        this.incomingConnector = incomingConnector;
        this.executorFactory = executorFactory;
        executorService = executorFactory.create("Incoming Connection Handler");
        incomingConnector.accept(incomingConnectionAction());
    }

    private Action<Connection<Message>> incomingConnectionAction() {
        return new Action<Connection<Message>>() {
            public void execute(Connection<Message> connection) {
                executorService.execute(new IncomingChannelHandler(connection));
            }
        };
    }

    public void stop() {
        executorService.stop();
    }

    public MultiChannelConnection<Message> listen() {
        lock.lock();
        try {
            URI localAddress;
            try {
                localAddress = new URI(String.format("channel:%s!%d", incomingConnector.getLocalAddress(),
                        nextConnectionId++));
            } catch (URISyntaxException e) {
                throw new UncheckedException(e);
            }
            DefaultMultiChannelConnection channelConnection = new DefaultMultiChannelConnection(executorFactory, String.format("Incoming Connection %s", localAddress), localAddress, null);
            pending.put(localAddress, channelConnection);
            return channelConnection;
        } finally {
            lock.unlock();
        }
    }

    public MultiChannelConnection<Message> connect(URI destinationAddress) {
        if (!destinationAddress.getScheme().equals("channel")) {
            throw new IllegalArgumentException(String.format("Cannot create a connection to destination URI '%s'.",
                    destinationAddress));
        }
        URI connectionAddress = toConnectionAddress(destinationAddress);
        Connection<Message> connection = outgoingConnector.connect(connectionAddress);
        connection.dispatch(new ConnectRequest(destinationAddress, null));
        DefaultMultiChannelConnection channelConnection = new DefaultMultiChannelConnection(executorFactory, String.format("Outgoing Connection %s", destinationAddress), null, destinationAddress);
        channelConnection.setConnection(connection);
        return channelConnection;
    }

    private URI toConnectionAddress(URI destinationAddress) {
        String content = destinationAddress.getSchemeSpecificPart();
        URI connectionAddress;
        try {
            connectionAddress = new URI(StringUtils.substringBeforeLast(content, "!"));
        } catch (URISyntaxException e) {
            throw new UncheckedException(e);
        }
        return connectionAddress;
    }

    private class IncomingChannelHandler implements Runnable {

        private final Connection<Message> connection;

        public IncomingChannelHandler(Connection<Message> connection) {
            this.connection = connection;
        }

        public void run() {
            ConnectRequest request = (ConnectRequest) connection.receive();
            DefaultMultiChannelConnection channelConnection;
            lock.lock();
            try {
                channelConnection = pending.remove(request.getDestinationAddress());
                if (channelConnection == null) {
                    throw new IllegalStateException(String.format(
                            "Request to connect received for unknown address '%s'.", request.getDestinationAddress()));
                }
            } finally {
                lock.unlock();
            }

            channelConnection.setConnection(connection);
        }
    }
}
