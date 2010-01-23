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
package org.gradle.messaging.dispatch;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpIncomingConnector implements IncomingConnector, AsyncStoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpIncomingConnector.class);
    private final ExecutorService executor;
    private final ServerSocketChannel serverSocket;
    private final URI localAddress;
    private final ClassLoader classLoader;

    public TcpIncomingConnector(ClassLoader classLoader) {
        this(Executors.newCachedThreadPool(), classLoader);
    }

    public TcpIncomingConnector(ExecutorService executor, ClassLoader classLoader) {
        this.executor = executor;
        this.classLoader = classLoader;
        
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(InetAddress.getByName(null), 0));
            localAddress = new URI(String.format("tcp://localhost:%d", serverSocket.socket().getLocalPort()));
        } catch (Exception e) {
            throw new GradleException(e);
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
                    URI remoteAddress = new URI(String.format("tcp://localhost:%d", ((InetSocketAddress)socket.socket().getRemoteSocketAddress()).getPort()));
                    action.execute(new SocketConnection(socket, localAddress, remoteAddress, classLoader));
                }
            } catch (AsynchronousCloseException e) {
                // Ignore
            } catch (Exception e) {
                LOGGER.warn("Could not accept remote connection.", e);
            }
        }
    }
}
