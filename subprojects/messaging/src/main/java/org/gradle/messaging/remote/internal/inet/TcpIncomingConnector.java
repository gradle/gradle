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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

public class TcpIncomingConnector implements IncomingConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final ExecutorFactory executorFactory;
    private final InetAddressFactory addressFactory;
    private final IdGenerator<?> idGenerator;

    public TcpIncomingConnector(ExecutorFactory executorFactory, InetAddressFactory addressFactory, IdGenerator<?> idGenerator) {
        this.executorFactory = executorFactory;
        this.addressFactory = addressFactory;
        this.idGenerator = idGenerator;
    }

    public ConnectionAcceptor accept(Action<ConnectCompletion> action, boolean allowRemote) {
        final ServerSocketChannel serverSocket;
        int localPort;
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(0));
            localPort = serverSocket.socket().getLocalPort();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        Object id = idGenerator.generateId();
        List<InetAddress> addresses = allowRemote ? addressFactory.findRemoteAddresses() : addressFactory.findLocalAddresses();
        final Address address = new MultiChoiceAddress(id, localPort, addresses);
        LOGGER.debug("Listening on {}.", address);

        final StoppableExecutor executor = executorFactory.create(String.format("Incoming %s TCP Connector on port %s", allowRemote ? "remote" : "local", localPort));
        executor.execute(new Receiver(serverSocket, action, allowRemote));

        return new ConnectionAcceptor() {
            public Address getAddress() {
                return address;
            }

            public void requestStop() {
                CompositeStoppable.stoppable(serverSocket).stop();
            }

            public void stop() {
                requestStop();
                executor.stop();
            }
        };
    }

    private class Receiver implements Runnable {
        private final ServerSocketChannel serverSocket;
        private final Action<ConnectCompletion> action;
        private final boolean allowRemote;

        public Receiver(ServerSocketChannel serverSocket, Action<ConnectCompletion> action, boolean allowRemote) {
            this.serverSocket = serverSocket;
            this.action = action;
            this.allowRemote = allowRemote;
        }

        public void run() {
            try {
                try {
                    while (true) {
                        final SocketChannel socket = serverSocket.accept();
                        InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.socket().getRemoteSocketAddress();
                        InetAddress remoteInetAddress = remoteSocketAddress.getAddress();
                        if (!allowRemote && !addressFactory.isLocal(remoteInetAddress)) {
                            LOGGER.error("Cannot accept connection from remote address {}.", remoteInetAddress);
                            socket.close();
                            continue;
                        }
                        LOGGER.debug("Accepted connection from {} to {}.", socket.socket().getRemoteSocketAddress(), socket.socket().getLocalSocketAddress());
                        action.execute(new SocketConnectCompletion(socket));
                    }
                } catch (ClosedChannelException e) {
                    // Ignore
                } catch (Exception e) {
                    LOGGER.error("Could not accept remote connection.", e);
                }
            } finally {
                CompositeStoppable.stoppable(serverSocket).stop();
            }
        }

    }

}
