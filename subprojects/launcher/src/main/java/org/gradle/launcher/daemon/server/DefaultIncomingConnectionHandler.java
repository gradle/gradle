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
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.DaemonFailure;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.launcher.daemon.server.exec.DaemonStateControl;
import org.gradle.messaging.remote.internal.Connection;

public class DefaultIncomingConnectionHandler implements IncomingConnectionHandler {
    private static final Logger LOGGER = Logging.getLogger(DefaultIncomingConnectionHandler.class);
    private final StoppableExecutor workers;
    private final DaemonContext daemonContext;
    private final DaemonCommandExecuter commandExecuter;
    private final DaemonStateControl stateControl;

    public DefaultIncomingConnectionHandler(DaemonCommandExecuter commandExecuter, StoppableExecutor workers, DaemonContext daemonContext, DaemonStateControl stateControl) {
        this.commandExecuter = commandExecuter;
        this.workers = workers;
        this.daemonContext = daemonContext;
        this.stateControl = stateControl;
    }

    public void handle(final Connection<Object> connection) {
        //we're spinning a thread to do work to avoid blocking the connection
        //This means that the Daemon potentially can do multiple things but we only allows a single build at a time
        workers.execute(new Runnable() {
            private Command command;

            public void run() {
                try {
                    command = (Command) connection.receive();
                    LOGGER.info("Daemon (pid: {}) received command: {}.", daemonContext.getPid(), command);
                } catch (Throwable e) {
                    String message = String.format("Unable to receive command from connection: '%s'", connection);
                    LOGGER.warn(message + ". Dispatching the failure to the daemon client...", e);
                    connection.dispatch(new DaemonFailure(new RuntimeException(message, e)));
                    //TODO SF exception handling / send typed exception / refactor / unit test and apply the same for below
                    return;
                }

                try {
                    LOGGER.debug(DaemonMessages.STARTED_EXECUTING_COMMAND + command + " with connection: " + connection + ".");
                    commandExecuter.executeCommand(connection, command, daemonContext, stateControl, new Runnable() {
                        public void run() {
                            // Don't care yet.
                        }
                    });
                } catch (Throwable e) {
                    String message = String.format("Uncaught exception when executing command: '%s' from connection: '%s'.", command, connection);
                    LOGGER.warn(message + ". Dispatching the failure to the daemon client...", e);
                    connection.dispatch(new DaemonFailure(new RuntimeException(message, e)));
                } finally {
                    LOGGER.debug(DaemonMessages.FINISHED_EXECUTING_COMMAND + command);
                }
            }
        });
    }
}
