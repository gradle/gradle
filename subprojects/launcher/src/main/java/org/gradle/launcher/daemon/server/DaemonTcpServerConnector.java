/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server;

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.inet.InetAddressFactory;
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector;
import org.gradle.util.UUIDGenerator;
import org.gradle.util.UncheckedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Opens a TCP connection for clients to connect to to communicate with a daemon.
 */
public class DaemonTcpServerConnector implements DaemonServerConnector {

    private static final Logger LOGGER = Logging.getLogger(DaemonServerConnector.class);

    final private TcpIncomingConnector<Object> incomingConnector;

    private boolean started;
    private boolean stopped;
    private final Lock lifecycleLock = new ReentrantLock();
    public static final String HELLO_MESSAGE = "Starting daemon server connector.";

    public DaemonTcpServerConnector() {
        this.incomingConnector = new TcpIncomingConnector<Object>(
                new DefaultExecutorFactory(),
                new DefaultMessageSerializer<Object>(getClass().getClassLoader()),
                new InetAddressFactory(),
                new UUIDGenerator()
        );
    }

    public Address start(final IncomingConnectionHandler handler) {
        lifecycleLock.lock();
        try {
            if (stopped) {
                throw new IllegalStateException("server connector cannot be started as it is either stopping or has been stopped");
            }
            if (started) {
                throw new IllegalStateException("server connector cannot be started as it has already been started");
            }

            // Hold the lock until we actually start accepting connections for the case when stop is called from another
            // thread while we are in the middle here.

            LOGGER.lifecycle(HELLO_MESSAGE);

            Action<ConnectEvent<Connection<Object>>> connectEvent = new Action<ConnectEvent<Connection<Object>>>() {
                public void execute(ConnectEvent<Connection<Object>> connectionConnectEvent) {
                    handler.handle(connectionConnectEvent.getConnection());
                }
            };

            try {
                final ServerSocket serverSocket = new ServerSocket(23000);
                new DefaultExecutorFactory().create("delete me!").execute(new Runnable() {
                    public void run() {
                        while (true) {
                            try {
                                final Socket socket = serverSocket.accept();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                final File currentDir = new File(reader.readLine());
                                final List<String> args = new ArrayList<String>();
                                while (true) {
                                    String line = reader.readLine();
                                    if (line == null || line.length() == 0) {
                                        break;
                                    }
                                    args.add(line);
                                }
                                System.out.format("=> CWD %s%n", currentDir);
                                System.out.format("=> ARGS %s%n", args);
                                
                                handler.handle(new Connection<Object>() {
                                    boolean receivedBuild;

                                    public void requestStop() {
                                    }

                                    public void dispatch(Object message) {
                                        try {
                                            socket.getOutputStream().write(("message:" + message + "\n").getBytes());
                                            socket.getOutputStream().flush();
                                        } catch (IOException e) {
                                            throw UncheckedException.asUncheckedException(e);
                                        }
                                    }

                                    public Object receive() {
                                        if (!receivedBuild) {
                                            CommandLineParser parser = new CommandLineParser();
                                            new DefaultCommandLineConverter().configure(parser);
                                            ParsedCommandLine parsedCommandLine = parser.parse(args);
                                            DefaultBuildActionParameters buildActionParameters = new DefaultBuildActionParameters(
                                                    new GradleLauncherMetaData(),
                                                    System.currentTimeMillis(),
                                                    new HashMap<Object, Object>(System.getProperties()),
                                                    Collections.<String, String>emptyMap(),
                                                    currentDir);
                                            receivedBuild = true;
                                            return new Build(new ExecuteBuildAction(currentDir, parsedCommandLine), buildActionParameters);
                                        } else {
                                            return new CloseInput(new GradleLauncherMetaData());
                                        }
                                    }

                                    public void stop() {
                                        try {
                                            socket.close();
                                        } catch (IOException e) {
                                            throw UncheckedException.asUncheckedException(e);
                                        }
                                    }
                                });
                            } catch (IOException e) {
                                
                                e.printStackTrace();
                            }
                        }
                    }
                }
                );
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }

            Address address = incomingConnector.accept(connectEvent, false);
            started = true;
            return address;
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void stop() {
        lifecycleLock.lock();
        try { // can't imagine what would go wrong here, but try/finally just in case
            stopped = true;
        } finally {
            lifecycleLock.unlock();
        }

        incomingConnector.stop();
    }

}