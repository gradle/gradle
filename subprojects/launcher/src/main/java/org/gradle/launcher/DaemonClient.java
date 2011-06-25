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
package org.gradle.launcher;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.protocol.*;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

/**
 * The client piece of the build daemon.
 *
 * <p>Protocol is this:</p>
 *
 * <ol> <li>Client connects to the server.</li>
 *
 * <li>Client sends a {@link org.gradle.launcher.protocol.Command} message.</li>
 *
 * <li>Server sends zero or more {@link org.gradle.logging.internal.OutputEvent} messages. Note that the server may send output messages before it receives the command message. </li>
 *
 * <li>Server sends a {@link org.gradle.launcher.protocol.CommandComplete} message.</li>
 *
 * <li>Connection is closed.</li>
 *
 * </ol>
 */
public class DaemonClient implements GradleLauncherActionExecuter<BuildActionParameters> {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private final DaemonConnector connector;
    private final BuildClientMetaData clientMetaData;
    private final OutputEventListener outputEventListener;

    public DaemonClient(DaemonConnector connector, BuildClientMetaData clientMetaData, OutputEventListener outputEventListener) {
        this.connector = connector;
        this.clientMetaData = clientMetaData;
        this.outputEventListener = outputEventListener;
    }

    /**
     * Stops the daemon, if it is running.
     */
    public void stop() {
        Connection<Object> connection = connector.maybeConnect();
        if (connection == null) {
            LOGGER.lifecycle("Gradle daemon is not running.");
            return;
        }
        run(new Stop(clientMetaData), connection);
        LOGGER.lifecycle("Gradle daemon stopped.");
    }

    /**
     * Executes the given action in the daemon. The action and parameters must be serializable.
     *
     * @param action The action
     * @throws ReportedException On failure, when the failure has already been logged/reported.
     */
    public <T> T execute(GradleLauncherAction<T> action, BuildActionParameters parameters) {
        LOGGER.warn("Note: the Gradle build daemon is an experimental feature.");
        LOGGER.warn("As such, you may experience unexpected build failures. You may need to occasionally stop the daemon.");
        Connection<Object> connection = connector.connect();
        Result result = (Result) run(new Build(action, parameters), connection);
        return (T) result.getResult();
    }

    private CommandComplete run(Command command, Connection<Object> connection) {
        try {
            connection.dispatch(command);
            while (true) {
                Object object = connection.receive();
                if (object instanceof CommandComplete) {
                    CommandComplete commandComplete = (CommandComplete) object;
                    if (commandComplete.getFailure() != null) {
                        throw commandComplete.getFailure();
                    }
                    return commandComplete;
                }
                OutputEvent outputEvent = (OutputEvent) object;
                outputEventListener.onOutput(outputEvent);
            }
        } finally {
            connection.stop();
        }
    }
}
