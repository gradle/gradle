/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.cli;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.launcher.bootstrap.ExecutionListener;

import java.util.Objects;

final class DebugLoggerWarningAction implements Action<ExecutionListener> {

    static final String WARNING_MESSAGE_BODY;

    static {
        @SuppressWarnings("StringBufferReplaceableByString") // Readability is better this way.
        final StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("#############################################################################\n");
        sb.append("   WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING\n");
        sb.append('\n');
        sb.append("   Debug level logging will leak security sensitive information!\n");
        sb.append('\n');
        sb.append("   ").append(new DocumentationRegistry().getDocumentationFor("logging", "sec:debug_security")).append('\n');
        sb.append("#############################################################################\n");
        WARNING_MESSAGE_BODY = sb.toString();
    }


    private final Logger logger;
    private final LoggingConfiguration loggingConfiguration;
    private final Action<ExecutionListener> action;

    DebugLoggerWarningAction(
        LoggingConfiguration loggingConfiguration,
        Action<ExecutionListener> action
    ) {
        this(Logging.getLogger(DebugLoggerWarningAction.class), loggingConfiguration, action);
    }

    @VisibleForTesting
    DebugLoggerWarningAction(
        Logger logger,
        LoggingConfiguration loggingConfiguration,
        Action<ExecutionListener> action
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.loggingConfiguration = Objects.requireNonNull(loggingConfiguration, "loggingConfiguration");
        this.action = Objects.requireNonNull(action, "action");
    }

    private void logWarningIfEnabled() {
        if (LogLevel.DEBUG.equals(loggingConfiguration.getLogLevel())) {
            logger.lifecycle(WARNING_MESSAGE_BODY);
        }
    }

    @Override
    public void execute(ExecutionListener executionListener) {
        // Add to the top of the log file.
        logWarningIfEnabled();
        try {
            action.execute(executionListener);
        } finally {
            // Add again to the bottom of the log file.
            logWarningIfEnabled();
        }
    }
}
