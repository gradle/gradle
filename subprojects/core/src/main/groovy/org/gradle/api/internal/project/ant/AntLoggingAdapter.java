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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.PrintStream;

public class AntLoggingAdapter implements BuildLogger {
    private final Logger logger = Logging.getLogger(AntLoggingAdapter.class);

    public void setMessageOutputLevel(int level) {
        // ignore
    }

    public void setOutputPrintStream(PrintStream output) {
        // ignore
    }

    public void setEmacsMode(boolean emacsMode) {
        // ignore
    }

    public void setErrorPrintStream(PrintStream err) {
        // ignore
    }

    public void buildStarted(BuildEvent event) {
        // ignore
    }

    public void buildFinished(BuildEvent event) {
        // ignore
    }

    public void targetStarted(BuildEvent event) {
        // ignore
    }

    public void targetFinished(BuildEvent event) {
        // ignore
    }

    public void taskStarted(BuildEvent event) {
        // ignore
    }

    public void taskFinished(BuildEvent event) {
        // ignore
    }

    public void messageLogged(BuildEvent event) {
        final StringBuffer message = new StringBuffer();
        if (event.getTask() != null) {
            String taskName = event.getTask().getTaskName();
            message.append("[ant:").append(taskName).append("] ");
        }
        final String messageText = event.getMessage();
        message.append(messageText);

        LogLevel level = Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER.get(event.getPriority());

        if (event.getException() != null) {
            logger.log(level, message.toString(), event.getException());
        } else {
            logger.log(level, message.toString());
        }
    }
}
