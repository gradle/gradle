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

public class LogToClient extends BuildCommandOnly {
    private static final Logger LOGGER = Logging.getLogger(LogToClient.class);

    private final LoggingOutputInternal loggingOutput;
    private final DaemonDiagnostics diagnostics;

    public LogToClient(LoggingOutputInternal loggingOutput, DaemonDiagnostics diagnostics) {
        this.loggingOutput = loggingOutput;
        this.diagnostics = diagnostics;
    }

    protected void doBuild(final DaemonCommandExecution execution, Build build) {
        OutputEventListener dispatcher = new DaemonConnectionLogDispatcher(execution.getConnection(), build.getParameters().getLogLevel());
        LOGGER.debug(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS);
        LOGGER.info("{}{}). The daemon log file: {}", DaemonMessages.STARTED_RELAYING_LOGS, diagnostics.getPid(), diagnostics.getDaemonLog());
        loggingOutput.addOutputEventListener(dispatcher);
        try {
            execution.proceed();
        } finally {
            loggingOutput.removeOutputEventListener(dispatcher);
        }
    }

    private class DaemonConnectionLogDispatcher implements OutputEventListener {
        private final DaemonConnection connection;
        private final LogLevel buildLogLevel;

        DaemonConnectionLogDispatcher(DaemonConnection conn, LogLevel buildLogLevel) {
            this.connection = conn;
            this.buildLogLevel = buildLogLevel;
        }

        @Override
        public void onOutput(OutputEvent event) {
            if (isMatchingBuildLogLevel(event) || isProgressEvent(event)) {
                connection.logEvent(event);
            }
        }

        private boolean isProgressEvent(OutputEvent event) {
            return event instanceof ProgressStartEvent || event instanceof ProgressEvent || event instanceof ProgressCompleteEvent;
        }

        private boolean isMatchingBuildLogLevel(OutputEvent event) {
            return event.getLogLevel() != null && event.getLogLevel().compareTo(buildLogLevel) >= 0;
        }
    }
}
