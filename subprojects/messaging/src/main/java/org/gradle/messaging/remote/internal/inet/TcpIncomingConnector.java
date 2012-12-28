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
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.IncomingConnector;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpIncomingConnector implements IncomingConnector, AsyncStoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final StoppableExecutor executor;
    private final InetAddressFactory addressFactory;
    private final IdGenerator<?> idGenerator;
    private final List<ServerSocketChannel> serverSockets = new CopyOnWriteArrayList<ServerSocketChannel>();

    public TcpIncomingConnector(ExecutorFactory executorFactory, InetAddressFactory addressFactory, IdGenerator<?> idGenerator) {
        this.addressFactory = addressFactory;
        this.idGenerator = idGenerator;
        this.executor = executorFactory.create("Incoming TCP Connector");
    }

    public <T> Address accept(Action<ConnectEvent<Connection<T>>> action, ClassLoader classLoader, boolean allowRemote) {
        return accept(action, new DefaultMessageSerializer<T>(classLoader), allowRemote);
    }

    public <T> Address accept(Action<ConnectEvent<Connection<T>>> action, MessageSerializer<T> serializer, boolean allowRemote) {
        ServerSocketChannel serverSocket;
        int localPort;
        try {
            serverSocket = ServerSocketChannel.open();
            serverSockets.add(serverSocket);
            serverSocket.socket().bind(new InetSocketAddress(0));
            localPort = serverSocket.socket().getLocalPort();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        Object id = idGenerator.generateId();
        List<InetAddress> addresses = allowRemote ? addressFactory.findRemoteAddresses() : addressFactory.findLocalAddresses();
        Address address = new MultiChoiceAddress(id, localPort, addresses);
        LOGGER.debug("Listening on {}.", address);

        executor.execute(new Receiver<T>(serverSocket, action, serializer, allowRemote));
        return address;
    }

    public void requestStop() {
        CompositeStoppable.stoppable(serverSockets).stop();
    }

    public void stop() {
        requestStop();
        executor.stop();
    }

    private class Receiver<T> implements Runnable {
        private final ServerSocketChannel serverSocket;
        private final Action<ConnectEvent<Connection<T>>> action;
        private final MessageSerializer<T> serializer;
        private final boolean allowRemote;

        public Receiver(ServerSocketChannel serverSocket, Action<ConnectEvent<Connection<T>>> action, MessageSerializer<T> serializer, boolean allowRemote) {
            this.serverSocket = serverSocket;
            this.action = action;
            this.serializer = serializer;
            this.allowRemote = allowRemote;
        }

        public void run() {
            try {
                try {
                    while (true) {
                        SocketChannel socket = serverSocket.accept();
                        InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.socket().getRemoteSocketAddress();
                        InetAddress remoteInetAddress = remoteSocketAddress.getAddress();
                        if (!allowRemote && !addressFactory.isLocal(remoteInetAddress)) {
                            LOGGER.error("Cannot accept connection from remote address {}.", remoteInetAddress);
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
                CompositeStoppable.stoppable(serverSocket).stop();
                serverSockets.remove(serverSocket);
            }
        }
    }
}
