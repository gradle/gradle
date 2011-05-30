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

package org.gradle.messaging.remote.internal.inet;

import org.gradle.api.Action;
import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.IncomingConnector;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.util.IdGenerator;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpIncomingConnector<T> implements IncomingConnector<T>, AsyncStoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final StoppableExecutor executor;
    private final MessageSerializer<T> serializer;
    private final IdGenerator<?> idGenerator;
    private final List<InetAddress> localAddresses;
    private final List<InetAddress> remoteAddresses;
    private final List<ServerSocketChannel> serverSockets = new CopyOnWriteArrayList<ServerSocketChannel>();

    public TcpIncomingConnector(ExecutorFactory executorFactory, MessageSerializer<T> serializer, InetAddressFactory addressFactory, IdGenerator<?> idGenerator) {
        this.serializer = serializer;
        this.idGenerator = idGenerator;
        this.executor = executorFactory.create("Incoming TCP Connector");

        localAddresses = addressFactory.findLocalAddresses();
        remoteAddresses = addressFactory.findRemoteAddresses();
    }

    public Address accept(Action<ConnectEvent<Connection<T>>> action, boolean allowRemote) {
        ServerSocketChannel serverSocket;
        int localPort;
        try {
            serverSocket = ServerSocketChannel.open();
            serverSockets.add(serverSocket);
            serverSocket.socket().bind(new InetSocketAddress(0));
            localPort = serverSocket.socket().getLocalPort();
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }

        Object id = idGenerator.generateId();
        List<InetAddress> addresses = allowRemote ? remoteAddresses : localAddresses;
        Address address = new MultiChoiceAddress(id, localPort, addresses);
        LOGGER.debug("Listening on {}.", address);

        executor.execute(new Receiver(serverSocket, action, allowRemote));
        return address;
    }

    public void requestStop() {
        new CompositeStoppable().addCloseables(serverSockets).stop();
    }

    public void stop() {
        requestStop();
        executor.stop();
    }

    private class Receiver implements Runnable {
        private final ServerSocketChannel serverSocket;
        private final Action<ConnectEvent<Connection<T>>> action;
        private final boolean allowRemote;

        public Receiver(ServerSocketChannel serverSocket, Action<ConnectEvent<Connection<T>>> action, boolean allowRemote) {
            this.serverSocket = serverSocket;
            this.action = action;
            this.allowRemote = allowRemote;
        }

        public void run() {
            try {
                try {
                    while (true) {
                        SocketChannel socket = serverSocket.accept();
                        InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.socket().getRemoteSocketAddress();
                        if (!allowRemote && !localAddresses.contains(remoteSocketAddress.getAddress())) {
                            LOGGER.error("Cannot accept connection from remote address {}.", remoteSocketAddress.getAddress());
                            socket.close();
                            continue;
                        }

                        SocketConnection<T> connection = new SocketConnection<T>(socket, serializer);
                        Address localAddress = connection.getLocalAddress();
                        Address remoteAddress = connection.getRemoteAddress();

                        LOGGER.debug("Accepted connection from {} to {}.", remoteAddress, localAddress);
                        action.execute(new ConnectEvent<Connection<T>>(connection, localAddress, remoteAddress));
                    }
                } catch (ClosedChannelException e) {
                    // Ignore
                } catch (Exception e) {
                    LOGGER.error("Could not accept remote connection.", e);
                }
            } finally {
                new CompositeStoppable(serverSocket).stop();
                serverSockets.remove(serverSocket);
            }
        }
    }
}
