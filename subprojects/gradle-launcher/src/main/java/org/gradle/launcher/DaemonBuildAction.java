/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.launcher.protocol.Build;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

import java.io.File;

public class DaemonBuildAction extends DaemonClientAction implements Action<ExecutionListener> {
    private static final Logger LOGGER = Logging.getLogger(DaemonBuildAction.class);
    private final DaemonConnector connector;
    private final StartParameter startParameter;
    private final ParsedCommandLine args;
    private final File currentDir;

    public DaemonBuildAction(OutputEventListener outputEventListener, DaemonConnector connector, StartParameter startParameter, ParsedCommandLine args, File currentDir) {
        super(outputEventListener);
        this.connector = connector;
        this.startParameter = startParameter;
        this.args = args;
        this.currentDir = currentDir;
    }

    public void execute(ExecutionListener executionListener) {
        LOGGER.warn("Note: the Gradle build daemon is an experimental feature.");
        LOGGER.warn("As such, you may experience unexpected build failures. You may need to occasionally stop the daemon.");
        Connection<Object> connection = connector.connect(startParameter);
        run(new Build(currentDir, args), connection, executionListener);
    }
}
