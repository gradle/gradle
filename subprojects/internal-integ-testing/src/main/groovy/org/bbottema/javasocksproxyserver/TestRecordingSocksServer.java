/*
 * Copyright 2022 the original author or authors.
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

package org.bbottema.javasocksproxyserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Alternate implementation of {@link SocksServer} that records connections which would have been
 * made, but does not actually make them.
 *
 * Must live in same package as {@link SocksServer}.
 */
public final class TestRecordingSocksServer extends SocksServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(org.bbottema.javasocksproxyserver.SocksServer.class);

    private List<InetAddress> connectionTargets = new CopyOnWriteArrayList<>();

    /**
     * Check the recorded connections for a specific address - this indicates that a connection would have been made to this address with a non-test {@link SocksServer}.
     *
     * @param target IP address to which to verify connection
     * @return {@code true} if the connection was made to the target; {@code false} otherwise
     */
    boolean madeConnectionTo(InetAddress target) {
        return connectionTargets.contains(target);
    }

    /**
     * Check that a connection was recorded for any address - this indicates that a connection would have been made to any address with a non-test {@link SocksServer}..
     *
     * @return {@code true} if the connection would have been made; {@code false} otherwise
     */
    boolean madeAnyConnection() {
        return connectionTargets.size() > 0;
    }

    public synchronized void start(int listenPort) {
        start(listenPort, ServerSocketFactory.getDefault());
    }

    public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
        this.stopping = false;
        new Thread(new ServerProcess(listenPort, serverSocketFactory)).start();
    }

    public synchronized void stop() {
        stopping = true;
    }

    private class ServerProcess implements Runnable {

        protected final int port;
        private final ServerSocketFactory serverSocketFactory;

        public ServerProcess(int port, ServerSocketFactory serverSocketFactory) {
            this.port = port;
            this.serverSocketFactory = serverSocketFactory;
        }

        @Override
        public void run() {
            LOGGER.debug("SOCKS server started...");
            try {
                handleClients(port);
                LOGGER.debug("SOCKS server stopped...");
            } catch (IOException e) {
                LOGGER.debug("SOCKS server crashed...");
                Thread.currentThread().interrupt();
            }
        }

        protected void handleClients(int port) throws IOException {
            final ServerSocket listenSocket = serverSocketFactory.createServerSocket(port);
            listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);

            LOGGER.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());

            while (true) {
                synchronized (TestRecordingSocksServer.this) {
                    if (stopping) {
                        break;
                    }
                }
                handleNextClient(listenSocket);
            }

            try {
                listenSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }

        private void handleNextClient(ServerSocket listenSocket) {
            try {
                final Socket clientSocket = listenSocket.accept();
                clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
                LOGGER.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
                new Thread(new TestRecordingProxyHandler(clientSocket, connectionTargets)).start();
            } catch (InterruptedIOException e) {
                // This exception is thrown when accept timeout is expired
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
