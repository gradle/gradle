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
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.*;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

import java.io.InputStream;

/**
 * The client piece of the build daemon.
 * <p>
 * Immediately upon forming a connection, the daemon may send {@link OutputEvent} messages back to the client and may do so
 * for as long as the connection is open.
 * <p>
 * The client is expected to send exactly one {@link Build} message as the first message it sends to the daemon. The daemon 
 * may either return {@link DaemonBusy} or {@link BuildStarted}. If the former is received, the client should not send any more
 * messages to this daemon. If the latter is received, the client can assume the daemon is performing the build. The client may then
 * send zero to many {@link ForwardInput} messages. If the client's stdin stream is closed before the connection to the
 * daemon is terminated, the client must send a {@link CloseInput} command to instruct the daemon that no more input is to be
 * expected.
 * <p>
 * After receiving the {@link Result} message (after a {@link BuildStarted} mesage), the client must send a {@link CloseInput}
 * command if it has not already done so due to the stdin stream being closed. At this point the client is expected to 
 * terminate the connection with the daemon.
 * <p>
 * If the daemon returns a {@code null} message before returning a {@link Result} object, it has terminated unexpectedly for some reason.
 */
public class DaemonClient implements GradleLauncherActionExecuter<BuildActionParameters> {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private final DaemonConnector connector;
    private final BuildClientMetaData clientMetaData;
    private final OutputEventListener outputEventListener;
    private final Spec<DaemonContext> compatibilitySpec;
    private final InputStream buildStandardInput;

    //TODO SF - outputEventListener and buildStandardInput are per-build settings
    //so down the road we should refactor the code accordingly and potentially attach them to BuildActionParameters
    public DaemonClient(DaemonConnector connector, BuildClientMetaData clientMetaData, OutputEventListener outputEventListener,
                        Spec<DaemonContext> compatibilitySpec, InputStream buildStandardInput) {
        this.connector = connector;
        this.clientMetaData = clientMetaData;
        this.outputEventListener = outputEventListener;
        this.compatibilitySpec = compatibilitySpec;
        this.buildStandardInput = buildStandardInput;
    }

    /**
     * Stops all daemons, if any is running.
     */
    public void stop() {
        Spec<DaemonContext> stoppableDaemonSpec = Specs.satisfyAll();
        DaemonConnection connection = connector.maybeConnect(stoppableDaemonSpec);
        if (connection == null) {
            LOGGER.lifecycle("No Gradle daemons are running.");
            return;
        }

        LOGGER.lifecycle("Stopping daemon.");
        //iterate and stop all daemons
        while (connection != null) {
            new StopDispatcher().dispatch(clientMetaData, connection.getConnection());
            LOGGER.lifecycle("Gradle daemon stopped.");
            connection = connector.maybeConnect(stoppableDaemonSpec);
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
            DaemonConnection daemonConnection = connector.connect(compatibilitySpec);
            Connection<Object> connection = daemonConnection.getConnection();
            Build build = new Build(action, parameters);

            Object firstResult;
            try {
                connection.dispatch(build);
                firstResult = connection.receive();
            } catch (Exception e) {
                //TODO SF find a way to test it.
                LOGGER.warn("Unable to receive the first result from the daemon. Trying a different daemon...", e);
                continue;
            }

            if (firstResult instanceof BuildStarted) {
                return (T) monitorBuild(build, connection).getValue();
            } else if (firstResult instanceof DaemonBusy) {
                LOGGER.info("The daemon we connected to was busy. Trying a different daemon...");
            } else if (firstResult instanceof Failure) {
                // Could potentially distinguish between CommandFailure and DaemonFailure here.
                throw ((Failure) firstResult).getValue();
            } else if (firstResult == null) {
                LOGGER.warn("The first result from the daemon was empty. Most likely the daemon has died. Trying a different daemon...");
            } else {
                throw new IllegalStateException(String.format("Daemon returned %s for which there is no strategy to handle (i.e. is an unknown Result type)", firstResult));
            }
        }
    }

    private Result monitorBuild(Build build, Connection<Object> connection) {
        DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(buildStandardInput, build.getClientMetaData(), connection);
        try {
            inputForwarder.start();
            int objectsReceived = 0;

            while (true) {
                Object object = connection.receive();
                LOGGER.debug("Received object #{}, type: {}", objectsReceived++, object == null ? null : object.getClass().getName());

                if (object == null) {
                    throw new DaemonDisappearedException(build, connection);
                } else if (object instanceof Failure) {
                    // Could potentially distinguish between CommandFailure and DaemonFailure here.
                    throw ((Failure) object).getValue();
                } else if (object instanceof OutputEvent) {
                    outputEventListener.onOutput((OutputEvent) object);
                } else if (object instanceof Result) {
                    return (Result) object;
                } else {
                    throw new IllegalStateException(String.format("Daemon returned %s (type: %s) as for which there is no strategy to handle", object, object.getClass()));
                }
            }
        } finally {
            inputForwarder.stop();
            connection.stop();
        }
    }
}
