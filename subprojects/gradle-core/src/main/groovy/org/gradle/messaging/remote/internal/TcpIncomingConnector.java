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
import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpIncomingConnector implements IncomingConnector, AsyncStoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final StoppableExecutor executor;
    private final ClassLoader classLoader;
    private final List<InetAddress> localAddresses;
    private final List<ServerSocketChannel> serverSockets = new CopyOnWriteArrayList<ServerSocketChannel>();

    public TcpIncomingConnector(ExecutorFactory executorFactory, ClassLoader classLoader) {
        this.executor = executorFactory.create("Incoming TCP Connector");
        this.classLoader = classLoader;

        localAddresses = TcpOutgoingConnector.findLocalAddresses();
    }

    public URI accept(Action<ConnectEvent<Connection<Object>>> action) {
        ServerSocketChannel serverSocket;
        URI localAddress;
        try {
            serverSocket = ServerSocketChannel.open();
            serverSockets.add(serverSocket);
            serverSocket.socket().bind(new InetSocketAddress(0));
            localAddress = new URI(String.format("tcp://localhost:%d", serverSocket.socket().getLocalPort()));
            LOGGER.debug("Listening on {}.", localAddress);
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }

        executor.execute(new Receiver(serverSocket, localAddress, action));
        return localAddress;
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
        private final URI localAddress;
        private final Action<ConnectEvent<Connection<Object>>> action;

        public Receiver(ServerSocketChannel serverSocket, URI localAddress, Action<ConnectEvent<Connection<Object>>> action) {
            this.serverSocket = serverSocket;
            this.localAddress = localAddress;
            this.action = action;
        }

        public void run() {
            try {
                while (true) {
                    SocketChannel socket = serverSocket.accept();
                    InetSocketAddress remoteAddress = (InetSocketAddress) socket.socket().getRemoteSocketAddress();
                    if (!localAddresses.contains(remoteAddress.getAddress())) {
                        LOGGER.error("Cannot accept connection from remote address {}.", remoteAddress.getAddress());
                    }
                    URI remoteUri = new URI(String.format("tcp://localhost:%d", remoteAddress.getPort()));
                    LOGGER.debug("Accepted connection from {}.", remoteUri);
                    action.execute(new ConnectEvent<Connection<Object>>(new SocketConnection<Object>(socket, localAddress, remoteUri, classLoader), localAddress, remoteUri));
                }
            } catch (ClosedChannelException e) {
                // Ignore
            } catch (Exception e) {
                LOGGER.error("Could not accept remote connection.", e);
            }
        }
    }
}
