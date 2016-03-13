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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.api.DaemonConnection;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class LogToClient extends BuildCommandOnly {

    public static final String DISABLE_OUTPUT = "org.gradle.daemon.disable-output";
    private static final Logger LOGGER = Logging.getLogger(LogToClient.class);

    private final LoggingOutputInternal loggingOutput;
    private final DaemonDiagnostics diagnostics;

    public LogToClient(LoggingOutputInternal loggingOutput, DaemonDiagnostics diagnostics) {
        this.loggingOutput = loggingOutput;
        this.diagnostics = diagnostics;
    }

    protected void doBuild(final DaemonCommandExecution execution, Build build) {
        if (Boolean.getBoolean(DISABLE_OUTPUT)) {
            execution.proceed();
            return;
        }

        final LogLevel buildLogLevel = build.getParameters().getLogLevel();
        final AsynchronousLogDispatcher dispatcher = new AsynchronousLogDispatcher(execution.getConnection());
        OutputEventListener listener = new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                if (event.getLogLevel() != null && event.getLogLevel().compareTo(buildLogLevel) >= 0) {
                    dispatcher.submit(event);
                }
            }
        };

        LOGGER.debug(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS);
        loggingOutput.addOutputEventListener(listener);
        try {
            LOGGER.info("{}{}). The daemon log file: {}", DaemonMessages.STARTED_RELAYING_LOGS, diagnostics.getPid(), diagnostics.getDaemonLog());
            dispatcher.start();
            execution.proceed();
        } finally {
            dispatcher.waitForCompletion();
            loggingOutput.removeOutputEventListener(listener);
        }
    }

    private class AsynchronousLogDispatcher extends Thread {
        private final BlockingQueue<OutputEvent> eventQueue = new LinkedBlockingDeque<OutputEvent>();
        private final DaemonConnection connection;
        private boolean shouldStop;

        private AsynchronousLogDispatcher(DaemonConnection conn) {
            this.connection = conn;
        }

        public void submit(OutputEvent event) {
            eventQueue.add(event);
        }

        @Override
        public void run() {
            while (!shouldStop) {
                try {
                    OutputEvent event = eventQueue.take();
                    dispatchAsync(event);
                } catch (InterruptedException ex) {
                    shouldStop = true;
                }
            }
        }

        private void sendRemainingEvents() {
            while (!eventQueue.isEmpty()) {
                OutputEvent event = eventQueue.remove();
                dispatchAsync(event);
            }
        }

        private void dispatchAsync(OutputEvent event) {
            try {
                connection.logEvent(event);
            } catch (Exception ex) {
                shouldStop = true;
                //Ignore. It means the client has disconnected so no point sending him any log output.
                //we should be checking if client still listens elsewhere anyway.
            }
        }

        public void waitForCompletion() {
            interrupt();
            sendRemainingEvents();
        }
    }
}
