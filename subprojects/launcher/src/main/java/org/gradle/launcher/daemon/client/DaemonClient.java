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

import com.google.common.collect.Lists;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.BuildEvent;
import org.gradle.launcher.daemon.protocol.BuildStarted;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.DaemonUnavailable;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.Finished;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.protocol.OutputMessage;
import org.gradle.launcher.daemon.protocol.Result;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.server.api.DaemonStoppedException;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;

import java.io.InputStream;
import java.util.List;

/**
 * The client piece of the build daemon.
 *
 * <p>To execute a build action:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link Build} message.</li>
 * <li>The daemon sends exactly one {@link BuildStarted}, {@link Failure} or {@link DaemonUnavailable} message.</li>
 * <li>If the build is started, the daemon may send zero or more {@link OutputMessage} messages.</li>
 * <li>If the build is started, the daemon may send zero or more {@link BuildEvent} messages.</li>
 * <li>If the build is started, the client may send zero or more {@link ForwardInput} messages followed by exactly one {@link CloseInput} message.</li>
 * <li>If the build is started, the client may send {@link org.gradle.launcher.daemon.protocol.Cancel} message before {@link CloseInput} message.</li>
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
    private final DaemonConnector connector;
    private final OutputEventListener outputEventListener;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final InputStream buildStandardInput;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;
    private final ProcessEnvironment processEnvironment;

    //TODO - outputEventListener and buildStandardInput are per-build settings
    //so down the road we should refactor the code accordingly and potentially attach them to BuildActionParameters
    public DaemonClient(DaemonConnector connector, OutputEventListener outputEventListener, ExplainingSpec<DaemonContext> compatibilitySpec,
                        InputStream buildStandardInput, ExecutorFactory executorFactory, IdGenerator<?> idGenerator, ProcessEnvironment processEnvironment) {
        this.connector = connector;
        this.outputEventListener = outputEventListener;
        this.compatibilitySpec = compatibilitySpec;
        this.buildStandardInput = buildStandardInput;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
        this.processEnvironment = processEnvironment;
    }

    protected IdGenerator<?> getIdGenerator() {
        return idGenerator;
    }

    protected DaemonConnector getConnector() {
        return connector;
    }

    /**
     * Executes the given action in the daemon. The action and parameters must be serializable.
     *
     * @param action The action
     * @throws org.gradle.initialization.ReportedException On failure, when the failure has already been logged/reported.
     */
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters parameters, ServiceRegistry contextServices) {
        Object buildId = idGenerator.generateId();
        List<DaemonInitialConnectException> accumulatedExceptions = Lists.newArrayList();

        LOGGER.debug("Executing build " + buildId + " in daemon client {pid=" + processEnvironment.maybeGetPid() + "}");

        int saneNumberOfAttempts = 100; //is it sane enough?

        for (int i = 1; i < saneNumberOfAttempts; i++) {
            final DaemonClientConnection connection = connector.connect(compatibilitySpec);
            try {
                Build build = new Build(buildId, connection.getDaemon().getToken(), action, requestContext.getClient(), requestContext.getStartTime(), parameters);
                return executeBuild(build, connection, requestContext.getCancellationToken(), requestContext.getEventConsumer());
            } catch (DaemonInitialConnectException e) {
                // this exception means that we want to try again.
                LOGGER.debug("{}, Trying a different daemon...", e.getMessage());
                accumulatedExceptions.add(e);
            } finally {
                connection.stop();
            }
        }

        throw new NoUsableDaemonFoundException("Unable to find a usable idle daemon. I have connected to "
            + saneNumberOfAttempts + " different daemons but I could not use any of them to run the build. BuildActionParameters were "
            + parameters + ".", accumulatedExceptions);
    }

    protected Object executeBuild(Build build, DaemonClientConnection connection, BuildCancellationToken cancellationToken, BuildEventConsumer buildEventConsumer) throws DaemonInitialConnectException {
        Object result;
        try {
            LOGGER.debug("Connected to daemon {}. Dispatching request {}.", connection.getDaemon(), build);
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

        LOGGER.debug("Received result {} from daemon {} (build should be starting).", result, connection.getDaemon());

        DaemonDiagnostics diagnostics = null;
        if (result instanceof BuildStarted) {
            diagnostics = ((BuildStarted) result).getDiagnostics();
            result = monitorBuild(build, diagnostics, connection, cancellationToken, buildEventConsumer);
        }

        LOGGER.debug("Received result {} from daemon {} (build should be done).", result, connection.getDaemon());

        connection.dispatch(new Finished());

        if (result instanceof Failure) {
            Throwable failure = ((Failure) result).getValue();
            if (failure instanceof DaemonStoppedException && cancellationToken.isCancellationRequested()) {
                LOGGER.error("Daemon was stopped to handle build cancel request.");
                throw new BuildCancelledException();
            }
            throw UncheckedException.throwAsUncheckedException(failure);
        } else if (result instanceof DaemonUnavailable) {
            throw new DaemonInitialConnectException("The daemon we connected to was unavailable: " + ((DaemonUnavailable) result).getReason());
        } else if (result instanceof Result) {
            return ((Result) result).getValue();
        } else {
            throw invalidResponse(result, build, diagnostics);
        }
    }

    private Object monitorBuild(Build build, DaemonDiagnostics diagnostics, Connection<Message> connection, BuildCancellationToken cancellationToken, BuildEventConsumer buildEventConsumer) {
        DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(buildStandardInput, connection, executorFactory);
        DaemonCancelForwarder cancelForwarder = new DaemonCancelForwarder(connection, cancellationToken);
        try {
            cancelForwarder.start();
            inputForwarder.start();
            int objectsReceived = 0;

            while (true) {
                Message object = connection.receive();
                LOGGER.trace("Received object #{}, type: {}", objectsReceived++, object == null ? null : object.getClass().getName());

                if (object == null) {
                    return handleDaemonDisappearance(build, diagnostics);
                } else if (object instanceof OutputMessage) {
                    outputEventListener.onOutput(((OutputMessage) object).getEvent());
                } else if (object instanceof BuildEvent) {
                    buildEventConsumer.dispatch(((BuildEvent) object).getPayload());
                } else {
                    return object;
                }
            }
        } finally {
            // Stop cancelling before sending end-of-input
            CompositeStoppable.stoppable(cancelForwarder, inputForwarder).stop();
        }
    }

    private Result handleDaemonDisappearance(Build build, DaemonDiagnostics diagnostics) {
        //we can try sending something to the daemon and try out if he is really dead or use jps
        //if he's really dead we should deregister it if it is not already deregistered.
        //if the daemon is not dead we might continue receiving from him (and try to find the bug in messaging infrastructure)
        LOGGER.error("The message received from the daemon indicates that the daemon has disappeared."
            + "\nBuild request sent: {}"
            + "\nAttempting to read last messages from the daemon log...", build);

        LOGGER.error(diagnostics.describe());
        throw new DaemonDisappearedException();
    }

    private IllegalStateException invalidResponse(Object response, Build command, DaemonDiagnostics diagnostics) {
        String diagnosticsMessage = diagnostics == null ? "No diagnostics available." : diagnostics.describe();
        return new IllegalStateException(String.format(
            "Received invalid response from the daemon: '%s' is a result of a type we don't have a strategy to handle. "
                + "Earlier, '%s' request was sent to the daemon. Diagnostics:\n%s", response, command, diagnosticsMessage));
    }
}
