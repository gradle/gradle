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
package org.gradle.launcher.daemon.client;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.daemon.protocol.*;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

/**
 * The client piece of the build daemon.
 * <p>
 * Immediately upon forming a connection, the daemon may send {@link OutputEvent} messages back to the client and may do so
 * for as long as the connection is open.
 * <p>
 * The client is expected to send exactly one {@link Build} message as the first message it sends to the daemon. After this
 * it may send zero to many {@link ForwardInput} messages. If the client's stdin stream is closed before the connection to the
 * daemon is terminated, the client must send a {@link CloseInput} command to instruct that daemon that no more input is to be
 * expected.
 * <p>
 * After receiving the {@link Build} message from the client, the daemon will at some time return a {@link Result} message
 * indicating either that the daemon encountered an internal failure or that the build failed dependending on the specific
 * type of the {@link Result} object returned.
 * <p>
 * After receiving the {@link Result} message, the client must send a {@link CloseInput} command if it has not already done so
 * due the stdin stream being closed. At this point the client is expected to terminate the connection with the daemon.
 * <p>
 * If the daemon returns a {@code null} message before returning a {@link Result} object, it has terminated unexpectedly for some reason.
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
     * Stops all daemons, if any is running.
     */
    public void stop() {
        DaemonConnection connection = connector.maybeConnect();
        if (connection == null) {
            LOGGER.lifecycle("No Gradle daemons are running.");
            return;
        }

        LOGGER.lifecycle("At least one daemon is running. Sending stop command...");
        //iterate and stop all daemons
        while (connection != null) {
            new StopDispatcher().dispatch(clientMetaData, connection.getConnection());
            LOGGER.lifecycle("Gradle daemon stopped.");
            connection = connector.maybeConnect();
        }
    }

    /**
     * Executes the given action in the daemon. The action and parameters must be serializable.
     *
     * @param action The action
     * @throws org.gradle.launcher.exec.ReportedException On failure, when the failure has already been logged/reported.
     */
    public <T> T execute(GradleLauncherAction<T> action, BuildActionParameters parameters) {
        LOGGER.warn("Note: the Gradle build daemon is an experimental feature.");
        LOGGER.warn("As such, you may experience unexpected build failures. You may need to occasionally stop the daemon.");
        while(true) {
            DaemonConnection daemonConnection = connector.connect();

            Result<T> result = runBuild(new Build(action, parameters), daemonConnection.getConnection());
            if (result instanceof DaemonBusy) {
                continue; // try a different daemon
            } else if (result instanceof Failure) {
                // Could potentially distinguish between CommandFailure and DaemonFailure here.
                throw ((Failure)result).getValue();
            } else if (result instanceof Success) {
                return result.getValue();
            } else {
                throw new IllegalStateException(String.format("Daemon returned %s for which there is no strategy to handle (i.e. is an unknown Result type)", result));
            }
        }
    }

    private <T> Result<T> runBuild(Build build, Connection<Object> connection) {
        DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(System.in, build.getClientMetaData(), connection);
        try {
            //TODO - this may fail. We should handle it and have tests for that. It means the server is gone.
            connection.dispatch(build);
            inputForwarder.start();
            while (true) {
                Object object = connection.receive();
                
                if (object == null) {
                    throw new DaemonDisappearedException(build, connection);
                } else if (object instanceof OutputEvent) {
                    outputEventListener.onOutput((OutputEvent) object);
                } else if (object instanceof Result) {
                    @SuppressWarnings("unchecked")
                    Result<T> result = (Result<T>) object;
                    return result;
                } else {
                    throw new IllegalStateException(String.format("Daemon returned %s (type: %s) for which there is no strategy to handle", object, object.getClass()));
                }
            }
        } finally {
            inputForwarder.stop();
            connection.stop();
        }
    }
}
