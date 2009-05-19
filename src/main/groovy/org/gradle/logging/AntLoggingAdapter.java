/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.logging;

import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.BuildEvent;
import org.gradle.api.logging.Logging;

import java.io.PrintStream;

/**
 * @author Hans Dockter
 */
public class AntLoggingAdapter implements BuildLogger {
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
        StringBuffer message = new StringBuffer();
        if (event.getTask() != null) {
            message.append("[ant:").append(event.getTask().getTaskName()).append("] ");
        }
        message.append(event.getMessage());
        if (event.getException() != null) {
            Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER.get(event.getPriority()).log(message.toString(), event.getException());
        } else {
            Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER.get(event.getPriority()).log(message.toString());
        }
    }
}
