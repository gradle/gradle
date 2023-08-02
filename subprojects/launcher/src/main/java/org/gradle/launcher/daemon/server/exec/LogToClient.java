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
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.api.DaemonConnection;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class LogToClient extends BuildCommandOnly {

    public static final String DISABLE_OUTPUT = "org.gradle.daemon.disable-output";
    private static final Logger LOGGER = Logging.getLogger(LogToClient.class);

    private final LoggingOutputInternal loggingOutput;
    private final DaemonDiagnostics diagnostics;

    private volatile AsynchronousLogDispatcher dispatcher;

    public LogToClient(LoggingOutputInternal loggingOutput, DaemonDiagnostics diagnostics) {
        this.loggingOutput = loggingOutput;
        this.diagnostics = diagnostics;
    }

    @Override
    protected void doBuild(final DaemonCommandExecution execution, Build build) {
        if (Boolean.getBoolean(DISABLE_OUTPUT)) {
            execution.proceed();
            return;
        }

        dispatcher = new AsynchronousLogDispatcher(execution.getConnection(), build.getParameters().getLogLevel());
        LOGGER.info("{}{}). The daemon log file: {}", DaemonMessages.STARTED_RELAYING_LOGS, diagnostics.getPid(), diagnostics.getDaemonLog());
        dispatcher.start();
        try {
            execution.proceed();
        } finally {
            dispatcher.waitForCompletion();
        }
    }

    private class AsynchronousLogDispatcher extends Thread {
        private final CountDownLatch completionLock = new CountDownLatch(1);
        private final Queue<OutputEvent> eventQueue = new ConcurrentLinkedQueue<OutputEvent>();
        private final DaemonConnection connection;
        private final OutputEventListener listener;
        private volatile boolean shouldStop;
        private boolean unableToSend;

        private AsynchronousLogDispatcher(DaemonConnection conn, final LogLevel buildLogLevel) {
            super("Asynchronous log dispatcher for " + conn);
            this.connection = conn;
            this.listener = new OutputEventListener() {
                @Override
                public void onOutput(OutputEvent event) {
                    if (dispatcher != null && (isMatchingBuildLogLevel(event) || isProgressEvent(event))) {
                        dispatcher.submit(event);
                    }
                }
                private boolean isProgressEvent(OutputEvent event) {
                    return event instanceof ProgressStartEvent || event instanceof ProgressEvent || event instanceof ProgressCompleteEvent;
                }
                private boolean isMatchingBuildLogLevel(OutputEvent event) {
                    return buildLogLevel != null && event.getLogLevel() != null && event.getLogLevel().compareTo(buildLogLevel) >= 0;
                }
            };
            LOGGER.debug(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS);
            loggingOutput.addOutputEventListener(listener);
        }

        public void submit(OutputEvent event) {
            eventQueue.add(event);
        }

        @Override
        public void run() {
            try {
                while (!shouldStop) {
                    OutputEvent event = eventQueue.poll();
                    if (event == null) {
                        Thread.sleep(10);
                    } else {
                        dispatchAsync(event);
                    }
                }
            } catch (InterruptedException ex) {
                // we must not use interrupt() because it would automatically
                // close the connection (sending data from an interrupted thread
                // automatically closes the connection)
                shouldStop = true;
            }
            sendRemainingEvents();
            completionLock.countDown();
        }

        private void sendRemainingEvents() {
            OutputEvent event;
            while ((event = eventQueue.poll()) != null) {
                dispatchAsync(event);
            }
        }

        private void dispatchAsync(OutputEvent event) {
            if (unableToSend) {
                return;
            }
            try {
                connection.logEvent(event);
            } catch (Exception ex) {
                shouldStop = true;
                unableToSend = true;
                //Ignore. It means the client has disconnected so no point sending him any log output.
                //we should be checking if client still listens elsewhere anyway.
            }
        }

        public void waitForCompletion() {
            loggingOutput.removeOutputEventListener(listener);
            shouldStop = true;
            try {
                completionLock.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
