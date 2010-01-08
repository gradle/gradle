/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.listener.remote;

import org.gradle.listener.dispatch.Dispatch;
import org.gradle.listener.dispatch.EndOfStream;
import org.gradle.listener.dispatch.Event;
import org.gradle.listener.dispatch.Message;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteReceiver implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteReceiver.class);
    private final Dispatch<? super Event> broadcaster;
    private final ServerSocketChannel serverSocket;
    private final ExceptionListener exceptionListener;
    private final ExecutorService executor;
    private final ClassLoader classLoader;

    public RemoteReceiver(Dispatch<? super Event> broadcaster) throws IOException {
        this(broadcaster, null, Thread.currentThread().getContextClassLoader());
    }

    public RemoteReceiver(Dispatch<? super Event> broadcaster, ClassLoader classLoader) throws IOException {
        this(broadcaster, null, classLoader);
    }

    public RemoteReceiver(Dispatch<? super Event> broadcaster, ExceptionListener exceptionListener,
                          ClassLoader classLoader) throws IOException {
        this.broadcaster = broadcaster;
        this.exceptionListener = exceptionListener;
        this.classLoader = classLoader;
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(InetAddress.getByName(null), 0));
        executor = Executors.newCachedThreadPool();
        executor.submit(new Receiver());
    }

    public int getBoundPort() {
        return serverSocket.socket().getLocalPort();
    }

    public void close() throws IOException {
        serverSocket.close();
        ThreadUtils.shutdown(executor);
    }

    private class Handler implements Runnable {
        private final SocketChannel socket;

        public Handler(SocketChannel socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                try {
                    InputStream inputStream = new BufferedInputStream(Channels.newInputStream(socket));
                    while (true) {
                        Message message = Message.receive(inputStream, classLoader);
                        if (message instanceof EndOfStream) {
                            // End of stream - finish with this connection
                            return;
                        }

                        Event event = (Event) message;
                        try {
                            broadcaster.dispatch(event);
                        } catch (Exception e) {
                            if (exceptionListener != null) {
                                exceptionListener.receiverThrewException(e);
                            } else {
                                throw e;
                            }
                        }
                    }
                } finally {
                    socket.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Could not handle remote event connection.", e);
            }
        }
    }

    private class Receiver implements Runnable {
        public void run() {
            try {
                while (true) {
                    SocketChannel socket = serverSocket.accept();
                    executor.submit(new Handler(socket));
                }
            } catch (AsynchronousCloseException e) {
                // Ignore
            } catch (IOException e) {
                LOGGER.warn("Could not accept remote event connection.", e);
            }
        }
    }

    public static interface ExceptionListener {
        public void receiverThrewException(Throwable throwable);
    }
}

