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
import org.gradle.initialization.BuildAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.*;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * The client piece of the build daemon.
 *
 * <p>To execute a build:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link Build} message.</li>
 * <li>The daemon sends exactly one {@link BuildStarted}, {@link Failure} or {@link DaemonUnavailable} message.</li>
 * <li>If the build is started, the daemon may send zero or more {@link OutputEvent} messages.</li>
 * <li>If the build is started, the client may send zero or more {@link ForwardInput} messages followed by exactly one {@link CloseInput} message.</li>
 * <li>The daemon sends exactly one {@link Result} message. It may no longer send any messages.</li>
 * <li>The client sends a {@link CloseInput} message, if not already sent. It may no longer send any {@link ForwardInput} messages.</li>
 * <li>The client sends a {@link Finished} message once it has received the {@link Result} message.
 *     It may no longer send any messages.</li>
 * <li>The client closes the connection.</li>
 * <li>The daemon closes the connection once it has received the {@link Finished} message.</li>
 * </ul>
 *
 * <p>To stop a daemon:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link Stop} message.</li>
 * <li>The daemon sends exactly one {@link Result} message. It may no longer send any messages.</li>
 * <li>The client sends a {@link Finished} message once it has received the {@link Result} message.
 *     It may no longer send any messages.</li>
 * <li>The client closes the connection.</li>
 * <li>The daemon closes the connection once it has received the {@link Finished} message.</li>
 * </ul>
 *
 * <p>
 * If the daemon returns a {@code null} message before returning a {@link Result} object, it has terminated unexpectedly for some reason.
 */
public class DaemonClient implements BuildActionExecuter<BuildActionParameters> {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private final DaemonConnector connector;
    private final OutputEventListener outputEventListener;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final InputStream buildStandardInput;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;

    //TODO - outputEventListener and buildStandardInput are per-build settings
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
    public <T> T execute(BuildAction<T> action, BuildActionParameters parameters) {
        Build build = new Build(idGenerator.generateId(), action, parameters);
        int saneNumberOfAttempts = 100; //is it sane enough?
        for (int i = 1; i < saneNumberOfAttempts; i++) {
            DaemonClientConnection connection = connector.connect(compatibilitySpec);
            try {
                return (T) executeBuild(build, connection);
            } catch (DaemonInitialConnectException e) {
                //this exception means that we want to try again.
                LOGGER.info(e.getMessage() + " Trying a different daemon...");
            } finally {
                connection.stop();
            }
        }
        //TODO it would be nice if below includes the errors that were accumulated above.
        throw new NoUsableDaemonFoundException("Unable to find a usable idle daemon. I have connected to "
                + saneNumberOfAttempts + " different daemons but I could not use any of them to run build: " + build + ".");
    }

    protected Object executeBuild(Build build, DaemonClientConnection connection) throws DaemonInitialConnectException {
        Object result;
        try {
            LOGGER.info("Connected to the daemon. Dispatching {} request.", build);
            connection.dispatch(build);
            result = connection.receive();
        } catch (StaleDaemonAddressException e) {
            LOGGER.debug("Connected to a stale daemon address.", e);
            //We might fail hard here on the assumption that something weird happened to the daemon.
            //However, since we haven't yet started running the build, we can recover by just trying again...
            throw new DaemonInitialConnectException("Connected to a stale daemon address.", e);
        }
        if (result == null) {
            throw new DaemonInitialConnectException("The first result from the daemon was empty. Most likely the process died immediately after connection.");
        }

        if (result instanceof BuildStarted) {
            DaemonDiagnostics diagnostics = ((BuildStarted) result).getDiagnostics();
            result = monitorBuild(build, diagnostics, connection);
        }

        connection.dispatch(new Finished());

        if (result instanceof Failure) {
            // Could potentially distinguish between CommandFailure and DaemonFailure here.
            throw UncheckedException.throwAsUncheckedException(((Failure) result).getValue());
        } else if (result instanceof DaemonUnavailable) {
            throw new DaemonInitialConnectException("The daemon we connected to was unavailable: " + ((DaemonUnavailable) result).getReason());
        } else if (result instanceof Result) {
            return ((Result) result).getValue();
        } else {
            throw invalidResponse(result, build);
        }
    }

    private Object monitorBuild(Build build, DaemonDiagnostics diagnostics, Connection<Object> connection) {
        DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(buildStandardInput, connection, executorFactory, idGenerator);
        try {
            inputForwarder.start();
            int objectsReceived = 0;

            while (true) {
                Object object = connection.receive();
                LOGGER.trace("Received object #{}, type: {}", objectsReceived++, object == null ? null : object.getClass().getName());

                if (object == null) {
                    return handleDaemonDisappearance(build, diagnostics);
                } else if (object instanceof OutputEvent) {
                    outputEventListener.onOutput((OutputEvent) object);
                } else {
                    return object;
                }
            }
        } finally {
            inputForwarder.stop();
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
        //TODO diagnostics could be included in the exception (they might be available).
        return new IllegalStateException(String.format(
                "Received invalid response from the daemon: '%s' is a result of a type we don't have a strategy to handle."
                        + "Earlier, '%s' request was sent to the daemon.", response, command));
    }
}
