/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.server.http;

import junit.framework.AssertionFailedError;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.os.OperatingSystem;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Allows the test process and a single build process to synchronize.
 */
public class CyclicBarrierHttpServer extends ExternalResource {
    private ExecutorService executor;
    private ServerSocketChannel serverSocket;
    private final Object lock = new Object();
    private boolean connected;
    private boolean released;
    private boolean stopped;

    @Override
    protected void before() {
        start();
    }

    @Override
    protected void after() {
        stop();
    }

    void start() {
        // Note: this is implemented using raw sockets. Originally implemented using Jetty, but some concurrency problems there caused Jetty to hang
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        executor = Executors.newCachedThreadPool();
        executor.execute(new Runnable() {
            public void run() {
                int i = 0;

                while (true) {
                    try {
                        SocketChannel connection;
                        try {
                            connection = serverSocket.accept();
                        } catch (AsynchronousCloseException e) {
                            // Socket has been closed, so we're stopping
                            return;
                        } catch (ClosedChannelException e) {
                            // Socket has been closed, so we're stopping
                            return;
                        }
                        try {
                            OutputStream outputStream = Channels.newOutputStream(connection);
                            System.out.println("Handle connection request no." + (++i));
                            handleConnection(outputStream);
                            outputStream.flush();
                        } finally {
                            connection.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            private void handleConnection(OutputStream outputStream) throws IOException {
                System.out.println("Handling HTTP request");

                synchronized (lock) {
                    if (connected) {
                        System.out.println("Received unexpected connection.");
                        outputStream.write("HTTP/1.1 500 Received an unexpected connection.\r\nConnection: close\r\nContent-length: 0\r\n\r\n".getBytes());
                        return;
                    }

                    System.out.println("Connection received");
                    connected = true;
                    lock.notifyAll();

                    long expiry = monotonicClockMillis() + 30000;
                    while (!released && !stopped) {
                        long delay = expiry - monotonicClockMillis();
                        if (delay <= 0) {
                            System.out.println("Timeout waiting for client to be released.");
                            outputStream.write("HTTP/1.1 500 Timeout waiting for client to be released.\r\nConnection: close\r\nContent-length: 0\r\n\r\n".getBytes());
                            return;
                        }
                        try {
                            lock.wait(delay);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (stopped) {
                        System.out.println("Releasing client on stop.");
                        outputStream.write("HTTP/1.1 500 Server stopped.\r\nConnection: close\r\nContent-length: 0\r\n\r\n".getBytes());
                        return;
                    }

                    connected = false;
                    released = false;
                    lock.notifyAll();
                }

                System.out.println("Sending response to client");
                outputStream.write("HTTP/1.1 200 Ok.\r\nConnection: close\r\nContent-length: 0\r\n\r\n".getBytes());
            }
        });
    }

    void stop() {
        System.out.println("Stopping server");
        synchronized (lock) {
            stopped = true;
            lock.notifyAll();
        }
        try {
            serverSocket.close();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public URI getUri() {
        int port = serverSocket.socket().getLocalPort();
        if (port <= 0) {
            throw new IllegalStateException(String.format("Unexpected port %s for HTTP server.", port));
        }
        try {
            return new URI(String.format("http://localhost:%s", port));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blocks until a connection to the URI has been received. No response is returned to the client until
     * {@link #release()} is called.
     */
    public void waitFor() {
        long expiry = monotonicClockMillis() + 20000;
        synchronized (lock) {
            while (!connected && !stopped) {
                long delay = expiry - monotonicClockMillis();
                if (delay <= 0) {
                    throw new AssertionFailedError(String.format("Timeout waiting for client to connect to %s.", getUri()));
                }
                System.out.println("waiting for client to connect");
                try {
                    lock.wait(delay);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (stopped) {
                throw new AssertionFailedError(String.format("Server was stopped while waiting for client to connect to %s.", getUri()));
            }
            System.out.println("client connected - unblocking");
        }
    }

    private long monotonicClockMillis() {
        return System.nanoTime() / 1000000L;
    }

    /**
     * Sends a response back on the connection.
     */
    public void release() {
        // TODO(radim): quick socket operation on Windows is not noticed by client
        // and it re-opens the connection immediately. Need to find a better way here.
        if (OperatingSystem.current().isWindows()) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        synchronized (lock) {
            released = true;
            lock.notifyAll();
        }
    }

    /**
     * Blocks until a connection to the URI has been received, then sends a response back to the client and returns.
     *
     * <p>Note that this method will generally return before the client has received the response.
     */
    public void sync() {
        synchronized (lock) {
            waitFor();
            release();
        }
    }
}
