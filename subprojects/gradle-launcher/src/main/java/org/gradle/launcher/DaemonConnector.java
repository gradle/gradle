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
package org.gradle.launcher;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.Message;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;
import org.gradle.util.UncheckedException;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DaemonConnector {
    private static final int DAEMON_PORT = 12345;
    private static final Logger LOGGER = Logging.getLogger(DaemonConnector.class);

    /**
     * Attempts to connect to the daemon, if it is running.
     *
     * @return The connection, or null if not running.
     */
    Connection<Object> maybeConnect() {
        try {
            Socket socket = new Socket(InetAddress.getByName(null), DAEMON_PORT);
            try {
                return new SocketConnection(socket);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        } catch (ConnectException e) {
            // Ignore
            return null;
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    /**
     * Connects to the daemon, starting it if required.
     * @return The connection. Never returns null.
     */
    Connection<Object> connect(StartParameter startParameter) {
        File userHomeDir = startParameter.getGradleUserHomeDir();

        Connection<Object> connection = maybeConnect();
        if (connection != null) {
            return connection;
        }

        LOGGER.lifecycle("Starting Gradle daemon");
        try {
            List<String> daemonArgs = new ArrayList<String>();
            daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
            daemonArgs.add("-Xmx1024m");
            daemonArgs.add("-XX:MaxPermSize=256m");
            daemonArgs.add("-cp");
            daemonArgs.add(GUtil.join(new DefaultClassPathRegistry().getClassPathFiles("GRADLE_RUNTIME"),
                    File.pathSeparator));
            daemonArgs.add(GradleDaemon.class.getName());
            ProcessBuilder builder = new ProcessBuilder(daemonArgs);
            builder.directory(userHomeDir);
            Process process = builder.start();
            process.getOutputStream().close();
            process.getErrorStream().close();
            process.getInputStream().close();
            Date expiry = new Date(System.currentTimeMillis() + 30000L);
            do {
                connection = maybeConnect();
                if (connection != null) {
                    return connection;
                }
                Thread.sleep(500L);
            } while (System.currentTimeMillis() < expiry.getTime());
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }

        throw new GradleException("Timeout waiting to connect to Gradle daemon.");
    }
    
    /**
     * Starts accepting connections.
     *
     * @param handler The handler for connections.
     */
    void accept(IncomingConnectionHandler handler) {
        try {
            ServerSocket serverSocket = new ServerSocket(DAEMON_PORT);
            try {
                final AtomicBoolean finished = new AtomicBoolean();
                while (!finished.get()) {
                    LOGGER.lifecycle("Waiting for request");
                    final Socket socket = serverSocket.accept();
                    try {
                        Connection<Object> connection = new SocketConnection(socket);
                        handler.handle(connection, new Stoppable() {
                            public void stop() {
                                finished.set(true);
                            }
                        });
                    } finally {
                        socket.close();
                    }
                }
            } finally {
                serverSocket.close();
            }
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private static class SocketConnection implements Connection<Object> {
        final OutputStream outputStream;
        final InputStream inputStream;
        private final Socket socket;

        public SocketConnection(Socket socket) throws IOException {
            this.socket = socket;
            outputStream = new BufferedOutputStream(socket.getOutputStream());
            inputStream = new BufferedInputStream(socket.getInputStream());
        }

        public void dispatch(Object message) {
            try {
                Message.send(message, outputStream);
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public Object receive() {
            try {
                return Message.receive(inputStream, getClass().getClassLoader());
            } catch (Exception e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public void requestStop() {
            throw new UnsupportedOperationException();
        }

        public void stop() {
            try {
                socket.close();
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }
    }
}