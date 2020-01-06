/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.server.api.DaemonConnection;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIncomingConnectionHandler implements IncomingConnectionHandler, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultIncomingConnectionHandler.class);
    private final ManagedExecutor workers;
    private final byte[] token;
    private final DaemonContext daemonContext;
    private final DaemonCommandExecuter commandExecuter;
    private final DaemonStateControl daemonStateControl;
    private final ExecutorFactory executorFactory;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<Connection<?>> inProgress = new HashSet<Connection<?>>();

    public DefaultIncomingConnectionHandler(DaemonCommandExecuter commandExecuter, DaemonContext daemonContext, DaemonStateControl daemonStateControl, ExecutorFactory executorFactory, byte[] token) {
        this.commandExecuter = commandExecuter;
        this.daemonContext = daemonContext;
        this.daemonStateControl = daemonStateControl;
        this.executorFactory = executorFactory;
        workers = executorFactory.create("Daemon");
        this.token = token;
    }

    @Override
    public void handle(final RemoteConnection<Message> connection) {
        // Mark the connection has being handled
        onStartHandling(connection);

        //we're spinning a thread to do work to avoid blocking the connection
        //This means that the Daemon potentially can do multiple things but we only allows a single build at a time

        workers.execute(new ConnectionWorker(connection));
    }

    private void onStartHandling(Connection<?> connection) {
        lock.lock();
        try {
            inProgress.add(connection);
        } finally {
            lock.unlock();
        }
    }

    private void onFinishHandling(Connection<?> connection) {
        lock.lock();
        try {
            inProgress.remove(connection);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks until all connections have been handled or abandoned.
     */
    @Override
    public void stop() {
        lock.lock();
        try {
            while (!inProgress.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private class ConnectionWorker implements Runnable {
        private final RemoteConnection<Message> connection;

        public ConnectionWorker(RemoteConnection<Message> connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            try {
                receiveAndHandleCommand();
            } finally {
                onFinishHandling(connection);
            }
        }

        private void receiveAndHandleCommand() {
            try {
                DefaultDaemonConnection daemonConnection = new DefaultDaemonConnection(connection, executorFactory);
                try {
                    Command command = receiveCommand(daemonConnection);
                    if (command != null) {
                        handleCommand(command, daemonConnection);
                    }
                } finally {
                    daemonConnection.stop();
                }
            } finally {
                connection.stop();
            }
        }

        private Command receiveCommand(DaemonConnection daemonConnection) {
            try {
                Command command = (Command) daemonConnection.receive(120, TimeUnit.SECONDS);
                LOGGER.info("Received command: {}.", command);
                return command;
            } catch (Throwable e) {
                LOGGER.warn(String.format("Unable to receive command from client %s. Discarding connection.", connection), e);
                return null;
            }
        }

        private void handleCommand(Command command, DaemonConnection daemonConnection) {
            LOGGER.debug("{}{} with connection: {}.", DaemonMessages.STARTED_EXECUTING_COMMAND, command, connection);
            try {
                if (!Arrays.equals(command.getToken(), token)) {
                    throw new BadlyFormedRequestException(String.format("Unexpected authentication token in command %s received from %s", command, connection));
                }
                commandExecuter.executeCommand(daemonConnection, command, daemonContext, daemonStateControl);
            } catch (Throwable e) {
                LOGGER.warn(String.format("Unable to execute command %s from %s. Dispatching the failure to the daemon client", command, connection), e);
                daemonConnection.completed(new Failure(e));
            } finally {
                LOGGER.debug("{}{}", DaemonMessages.FINISHED_EXECUTING_COMMAND, command);
            }

            Object finished = daemonConnection.receive(60, TimeUnit.SECONDS);
            LOGGER.debug("Received finished message: {}", finished);
        }
    }
}
