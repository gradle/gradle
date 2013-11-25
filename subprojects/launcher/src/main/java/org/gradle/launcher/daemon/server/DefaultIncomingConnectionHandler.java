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
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.DaemonFailure;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.launcher.daemon.server.exec.DaemonConnection;
import org.gradle.launcher.daemon.server.exec.DaemonStateControl;
import org.gradle.messaging.remote.internal.Connection;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultIncomingConnectionHandler implements IncomingConnectionHandler, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultIncomingConnectionHandler.class);
    private final StoppableExecutor workers;
    private final DaemonContext daemonContext;
    private final DaemonCommandExecuter commandExecuter;
    private final DaemonStateControl daemonStateControl;
    private final ExecutorFactory executorFactory;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<Connection<?>> inProgress = new HashSet<Connection<?>>();

    public DefaultIncomingConnectionHandler(DaemonCommandExecuter commandExecuter, DaemonContext daemonContext, DaemonStateControl daemonStateControl, ExecutorFactory executorFactory) {
        this.commandExecuter = commandExecuter;
        this.daemonContext = daemonContext;
        this.daemonStateControl = daemonStateControl;
        this.executorFactory = executorFactory;
        workers = executorFactory.create("Daemon");
    }

    public void handle(final Connection<Object> connection) {
        // Mark the connection has being handled
        onStartHandling(connection);

        //we're spinning a thread to do work to avoid blocking the connection
        //This means that the Daemon potentially can do multiple things but we only allows a single build at a time

        workers.execute(new ConnectionWorker(connection));
    }

    private void onStartHandling(Connection<Object> connection) {
        lock.lock();
        try {
            inProgress.add(connection);
        } finally {
            lock.unlock();
        }
    }

    private void onFinishHandling(Connection<Object> connection) {
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
        private final Connection<Object> connection;

        public ConnectionWorker(Connection<Object> connection) {
            this.connection = connection;
        }

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
                String message = String.format("Unable to receive command from connection: '%s'", connection);
                LOGGER.warn(message + ". Dispatching the failure to the daemon client...", e);
                daemonConnection.completed(new DaemonFailure(new RuntimeException(message, e)));
                //TODO SF exception handling / send typed exception / refactor / unit test and apply the same for below
                return null;
            }
        }

        private void handleCommand(Command command, DaemonConnection daemonConnection) {
            LOGGER.debug(DaemonMessages.STARTED_EXECUTING_COMMAND + command + " with connection: " + connection + ".");
            try {
                commandExecuter.executeCommand(daemonConnection, command, daemonContext, daemonStateControl, new Runnable() {
                    public void run() {
                        onFinishHandling(connection);
                    }
                });
            } catch (Throwable e) {
                String message = String.format("Uncaught exception when executing command: '%s' from connection: '%s'.", command, connection);
                LOGGER.warn(message + ". Dispatching the failure to the daemon client...", e);
                daemonConnection.completed(new DaemonFailure(new RuntimeException(message, e)));
            } finally {
                LOGGER.debug(DaemonMessages.FINISHED_EXECUTING_COMMAND + command);
            }

            Object finished = daemonConnection.receive(60, TimeUnit.SECONDS);
            LOGGER.debug("Received finished message: {}", finished);
        }
    }
}
