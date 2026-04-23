/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project.ant;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildLogger;
import org.gradle.api.AntBuilder.AntMessagePriority;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.LogLevelMapping;

import java.io.PrintStream;

public class AntLoggingAdapter implements BuildLogger {
    private final Logger logger = Logging.getLogger(AntLoggingAdapter.class);

    private AntMessagePriority lifecycleLogLevel;

    @Override
    public void setMessageOutputLevel(int level) {
        // ignore
    }

    @Override
    public void setOutputPrintStream(PrintStream output) {
        // ignore
    }

    @Override
    public void setEmacsMode(boolean emacsMode) {
        // ignore
    }

    @Override
    public void setErrorPrintStream(PrintStream err) {
        // ignore
    }

    @Override
    public void buildStarted(BuildEvent event) {
        // ignore
    }

    @Override
    public void buildFinished(BuildEvent event) {
        // ignore
    }

    @Override
    public void targetStarted(BuildEvent event) {
        // ignore
    }

    @Override
    public void targetFinished(BuildEvent event) {
        // ignore
    }

    @Override
    public void taskStarted(BuildEvent event) {
        // ignore
    }

    @Override
    public void taskFinished(BuildEvent event) {
        // ignore
    }

    @Override
    public void messageLogged(BuildEvent event) {
        final StringBuffer message = new StringBuffer();
        if (event.getTask() != null) {
            String taskName = event.getTask().getTaskName();
            message.append("[ant:").append(taskName).append("] ");
        }
        final String messageText = event.getMessage();
        message.append(messageText);

        LogLevel level = getLogLevelForMessagePriority(event.getPriority());

        if (event.getException() != null) {
            logger.log(level, message.toString(), event.getException());
        } else {
            logger.log(level, message.toString());
        }
    }

    public void setLifecycleLogLevel(String lifecycleLogLevel) {
        setLifecycleLogLevel(lifecycleLogLevel == null ? null : AntMessagePriority.valueOf(lifecycleLogLevel));
    }

    public void setLifecycleLogLevel(AntMessagePriority lifecycleLogLevel) {
        this.lifecycleLogLevel = lifecycleLogLevel;
    }

    public AntMessagePriority getLifecycleLogLevel() {
        return lifecycleLogLevel;
    }

    private LogLevel getLogLevelForMessagePriority(int messagePriority) {
        LogLevel defaultLevel = LogLevelMapping.ANT_IVY_2_SLF4J.get(messagePriority);

        // Check to see if we should adjust the level based on a set lifecycle log level
        if (lifecycleLogLevel != null) {
            if (defaultLevel.ordinal() < LogLevel.LIFECYCLE.ordinal()
                && AntMessagePriority.from(messagePriority).ordinal() >= lifecycleLogLevel.ordinal()) {
                // we would normally log at a lower level than lifecycle, but the Ant message priority is actually higher
                // than (or equal to) the set lifecycle log level
                return LogLevel.LIFECYCLE;
            } else if (defaultLevel.ordinal() >= LogLevel.LIFECYCLE.ordinal()
                && AntMessagePriority.from(messagePriority).ordinal() < lifecycleLogLevel.ordinal()) {
                // would normally log at a level higher than (or equal to) lifecycle, but the Ant message priority is
                // actually lower than the set lifecycle log level
                return LogLevel.INFO;
            }
        }

        return defaultLevel;
    }
}
