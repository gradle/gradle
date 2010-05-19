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
import org.gradle.api.UncheckedIOException;
import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.util.ThreadUtils;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpIncomingConnector implements IncomingConnector, AsyncStoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final ExecutorService executor;
    private final ServerSocketChannel serverSocket;
    private final URI localAddress;
    private final ClassLoader classLoader;
    private final List<InetAddress> localAddresses;

    public TcpIncomingConnector(ClassLoader classLoader) {
        this(Executors.newCachedThreadPool(), classLoader);
    }

    public TcpIncomingConnector(ExecutorService executor, ClassLoader classLoader) {
        this.executor = executor;
        this.classLoader = classLoader;

        localAddresses = TcpOutgoingConnector.findLocalAddresses();
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(0));
            localAddress = new URI(String.format("tcp://localhost:%d", serverSocket.socket().getLocalPort()));
            LOGGER.debug("Listening on {}.", localAddress);
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    public URI getLocalAddress() {
        return localAddress;
    }

    public void accept(Action<Connection<Message>> action) {
        executor.submit(new Receiver(action));
    }

    public void requestStop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void stop() {
        requestStop();
        ThreadUtils.shutdown(executor);
    }

    private class Receiver implements Runnable {
        private final Action<Connection<Message>> action;

        public Receiver(Action<Connection<Message>> action) {
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
                    action.execute(new SocketConnection(socket, localAddress, remoteUri, classLoader));
                }
            } catch (AsynchronousCloseException e) {
                // Ignore
            } catch (Exception e) {
                LOGGER.error("Could not accept remote connection.", e);
            }
        }
    }
}
