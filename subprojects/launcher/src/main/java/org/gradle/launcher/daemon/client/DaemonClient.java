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

import org.gradle.api.GradleException;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.*;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * The client piece of the build daemon.
 * <p>
 * Immediately upon forming a connection, the daemon may send {@link OutputEvent} messages back to the client and may do so
 * for as long as the connection is open.
 * <p>
 * The client is expected to send exactly one {@link Build} message as the first message it sends to the daemon. The daemon 
 * may either return {@link org.gradle.launcher.daemon.protocol.DaemonUnavailable} or {@link BuildStarted}. If the former is received, the client should not send any more
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
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private final DaemonConnector connector;
    private final OutputEventListener outputEventListener;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final InputStream buildStandardInput;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;

    //TODO SF - outputEventListener and buildStandardInput are per-build settings
    //so down the road we should refactor the code accordingly and potentially attach them to BuildActionParameters
    public DaemonClient(DaemonConnector connector, OutputEventListener outputEventListener, ExplainingSpec<DaemonContext> compatibilitySpec,
                        InputStream buildStandardInput, ExecutorFactory executorFactory, IdGenerator<?> idGenerator) {
        this.connector = connector;
        this.outputEventListener = outputEventListener;
        this.compatibilitySpec = compatibilitySpec;
        this.buildStandardInput = buildStandardInput;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
    }

    protected IdGenerator<?> getIdGenerator() {
        return idGenerator;
    }

    protected DaemonConnector getConnector() {
        return connector;
    }

    /**
     * Stops all daemons, if any is running.
     */
    public void stop() {
        long start = System.currentTimeMillis();
        long expiry = start + STOP_TIMEOUT_SECONDS * 1000;
        Set<String> stopped = new HashSet<String>();

        // TODO - only connect to daemons that we have not yet sent a stop request to
        DaemonClientConnection connection = connector.maybeConnect(compatibilitySpec);
        if (connection == null) {
            LOGGER.lifecycle(DaemonMessages.NO_DAEMONS_RUNNING);
            return;
        }

        LOGGER.lifecycle("Stopping daemon(s).");

        //iterate and stop all daemons
        while (connection != null && System.currentTimeMillis() < expiry) {
            try {
                if (stopped.add(connection.getUid())) {
                    new StopDispatcher(idGenerator).dispatch(connection);
                    LOGGER.lifecycle("Gradle daemon stopped.");
                }
            } finally {
                connection.stop();
            }
            connection = connector.maybeConnect(compatibilitySpec);
        }

        if (connection != null) {
            throw new GradleException(String.format("Timeout waiting for all daemons to stop. Waited %s seconds.", (System.currentTimeMillis() - start) / 1000));
        }
    }

    /**
     * Executes the given action in the daemon. The action and parameters must be serializable.
     *
     * @param action The action
     * @throws org.gradle.launcher.exec.ReportedException On failure, when the failure has already been logged/reported.
     */
    public <T> T execute(GradleLauncherAction<T> action, BuildActionParameters parameters) {
        Build build = new Build(idGenerator.generateId(), action, parameters);
        int saneNumberOfAttempts = 100; //is it sane enough?
        for (int i = 1; i < saneNumberOfAttempts; i++) {
            DaemonClientConnection connection = connector.connect(compatibilitySpec);

            try {
                return (T) executeBuild(build, connection);
            } catch (DaemonInitialConnectException e) {
                //this exception means that we want to try again.
                LOGGER.info(e.getMessage() + " Trying a different daemon...");
            }
        }
        //TODO SF if we want to keep below sanity it should include the errors that were accumulated above.
        throw new NoUsableDaemonFoundException("Unable to find a usable idle daemon. I have connected to "
                + saneNumberOfAttempts + " different daemons but I could not use any of them to run build: " + build + ".");
    }

    protected Object executeBuild(Build build, DaemonClientConnection connection) throws DaemonInitialConnectException {
        Object firstResult;
        try {
            LOGGER.info("Connected to the daemon. Dispatching {} request.", build);
            connection.dispatch(build);
            firstResult = connection.receive();
        } catch (Exception e) {
            LOGGER.debug("Unable to perform initial dispatch/receive with the daemon.", e);
            //We might fail hard here on the assumption that something weird happened to the daemon.
            //However, since we haven't yet started running the build, we can recover by just trying again...
            throw new DaemonInitialConnectException("Problem when attempted to send and receive first result from the daemon.");
        }

        if (firstResult instanceof BuildStarted) {
            DaemonDiagnostics diagnostics = ((BuildStarted) firstResult).getDiagnostics();
            return monitorBuild(build, diagnostics, connection).getValue();
        } else if (firstResult instanceof Failure) {
            // Could potentially distinguish between CommandFailure and DaemonFailure here.
            throw UncheckedException.throwAsUncheckedException(((Failure) firstResult).getValue());
        } else if (firstResult instanceof DaemonUnavailable) {
            throw new DaemonInitialConnectException("The daemon we connected to was unavailable: " + ((DaemonUnavailable) firstResult).getReason());
        } else if (firstResult == null) {
            throw new DaemonInitialConnectException("The first result from the daemon was empty. Most likely the process died immediately after connection.");
        } else {
            throw invalidResponse(firstResult, build);
        }
    }

    private Result monitorBuild(Build build, DaemonDiagnostics diagnostics, Connection<Object> connection) {
        DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(buildStandardInput, connection, executorFactory, idGenerator);
        try {
            inputForwarder.start();
            int objectsReceived = 0;

            while (true) {
                Object object = connection.receive();
                LOGGER.trace("Received object #{}, type: {}", objectsReceived++, object == null ? null : object.getClass().getName());

                if (object == null) {
                    return handleDaemonDisappearance(build, diagnostics);
                } else if (object instanceof Failure) {
                    // Could potentially distinguish between CommandFailure and DaemonFailure here.
                    throw UncheckedException.throwAsUncheckedException(((Failure) object).getValue());
                } else if (object instanceof OutputEvent) {
                    outputEventListener.onOutput((OutputEvent) object);
                } else if (object instanceof Result) {
                    return (Result) object;
                } else {
                    throw invalidResponse(object, build);
                }
            }
        } finally {
            inputForwarder.stop();
            connection.stop();
        }
    }

    private Result handleDaemonDisappearance(Build build, DaemonDiagnostics diagnostics) {
        //we can try sending something to the daemon and try out if he is really dead or use jps
        //if he's really dead we should deregister it if it is not already deregistered.
        //if the daemon is not dead we might continue receiving from him (and try to find the bug in messaging infrastructure)
        LOGGER.error("The message received from the daemon indicates that the daemon has disappeared."
                + "\nBuild request sent: " + build
                + "\nAttempting to read last messages from the daemon log...");

        LOGGER.error(diagnostics.describe());

        throw new DaemonDisappearedException();
    }

    private IllegalStateException invalidResponse(Object response, Build command) {
        //TODO SF we could include diagnostics here (they might be available).
        return new IllegalStateException(String.format(
                "Received invalid response from the daemon: '%s' is a result of a type we don't have a strategy to handle."
                        + "Earlier, '%s' request was sent to the daemon.", response, command));
    }
}
