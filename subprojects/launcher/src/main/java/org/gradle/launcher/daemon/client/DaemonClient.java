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
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.remote.internal.Connection;
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
import org.gradle.launcher.exec.BuildActionResult;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
public class DaemonClient implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private final DaemonConnector connector;
    private final OutputEventListener outputEventListener;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final InputStream buildStandardInput;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<UUID> idGenerator;
    private final ProcessEnvironment processEnvironment;

    //TODO - outputEventListener and buildStandardInput are per-build settings
    //so down the road we should refactor the code accordingly and potentially attach them to BuildActionParameters
    public DaemonClient(DaemonConnector connector, OutputEventListener outputEventListener, ExplainingSpec<DaemonContext> compatibilitySpec,
                        InputStream buildStandardInput, ExecutorFactory executorFactory, IdGenerator<UUID> idGenerator, ProcessEnvironment processEnvironment) {
        this.connector = connector;
        this.outputEventListener = outputEventListener;
        this.compatibilitySpec = compatibilitySpec;
        this.buildStandardInput = buildStandardInput;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
        this.processEnvironment = processEnvironment;
    }

    protected IdGenerator<UUID> getIdGenerator() {
        return idGenerator;
    }

    protected DaemonConnector getConnector() {
        return connector;
    }

    /**
     * Executes the given action in the daemon. The action and parameters must be serializable.
     *
     * @param action The action
     */
    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters parameters, BuildRequestContext requestContext) {
        UUID buildId = idGenerator.generateId();
        List<DaemonInitialConnectException> accumulatedExceptions = Lists.newArrayList();

        LOGGER.debug("Executing build {} in daemon client {pid={}}", buildId, processEnvironment.maybeGetPid());

        // Attempt to connect to an existing idle and compatible daemon
        int saneNumberOfAttempts = 100; //is it sane enough?
        for (int i = 1; i < saneNumberOfAttempts; i++) {
            final DaemonClientConnection connection = connector.connect(compatibilitySpec);
            // No existing, compatible daemon is available to try
            if (connection == null) {
                break;
            }
            // Compatible daemon was found, try it
            try {
                Build build = new Build(buildId, connection.getDaemon().getToken(), action, requestContext.getClient(), requestContext.getStartTime(), requestContext.isInteractive(), parameters);
                return executeBuild(build, connection, requestContext.getCancellationToken(), requestContext.getEventConsumer());
            } catch (DaemonInitialConnectException e) {
                // this exception means that we want to try again.
                LOGGER.debug("{}, Trying a different daemon...", e.getMessage());
                accumulatedExceptions.add(e);
            } finally {
                connection.stop();
            }
        }

        // No existing daemon was usable, so start a new one and try it once
        final DaemonClientConnection connection = connector.startDaemon(compatibilitySpec);
        try {
            Build build = new Build(buildId, connection.getDaemon().getToken(), action, requestContext.getClient(), requestContext.getStartTime(), requestContext.isInteractive(), parameters);
            return executeBuild(build, connection, requestContext.getCancellationToken(), requestContext.getEventConsumer());
        } catch (DaemonInitialConnectException e) {
            // This means we could not connect to the daemon we just started.  fail and don't try again
            throw new NoUsableDaemonFoundException("A new daemon was started but could not be connected to: " +
                "pid=" + connection.getDaemon() + ", address= " + connection.getDaemon().getAddress() + ".",
                accumulatedExceptions);
        } finally {
            connection.stop();
        }
    }

    protected BuildActionResult executeBuild(Build build, DaemonClientConnection connection, BuildCancellationToken cancellationToken, BuildEventConsumer buildEventConsumer) throws DaemonInitialConnectException {
        Object result;
        try {
            LOGGER.debug("Connected to daemon {}. Dispatching request {}.", connection.getDaemon(), build);
            connection.dispatch(build);
            result = connection.receive();
        } catch (StaleDaemonAddressException e) {
            LOGGER.debug("Connected to a stale daemon address.", e);
            // We might fail hard here on the assumption that something weird happened to the daemon.
            // However, since we haven't yet started running the build, we can recover by just trying again.
            throw new DaemonInitialConnectException("Connected to a stale daemon address.", e);
        }

        if (result == null) {
            // If the response from the daemon is unintelligible, mark the daemon as unavailable so other
            // clients won't try to communicate with it. We'll attempt to recovery by trying again.
            connector.markDaemonAsUnavailable(connection.getDaemon());
            throw new DaemonInitialConnectException("The first result from the daemon was empty. The daemon process may have died or a non-daemon process is reusing the same port.");
        }

        LOGGER.debug("Received result {} from daemon {} (build should be starting).", result, connection.getDaemon());

        DaemonDiagnostics diagnostics = null;
        if (result instanceof BuildStarted) {
            diagnostics = ((BuildStarted) result).getDiagnostics();
            result = monitorBuild(build, diagnostics, connection, cancellationToken, buildEventConsumer);
        }

        LOGGER.debug("Received result {} from daemon {} (build should be done).", result, connection.getDaemon());

        // If we get an error here, it means the daemon has already closed the connection.  This might occur because the
        // client is slow to send the Finished message, or because the daemon has expired for some reason.  Whatever the reason,
        // this is not important to the client at this point, so we just log it and continue.
        try {
            connection.dispatch(new Finished());
        } catch (DaemonConnectionException e) {
            LOGGER.debug("Could not send finished message to the daemon.", e);
        }

        if (result instanceof Failure) {
            Throwable failure = ((Failure) result).getValue();
            if (failure instanceof DaemonStoppedException && cancellationToken.isCancellationRequested()) {
                return BuildActionResult.cancelled(new BuildCancelledException("Daemon was stopped to handle build cancel request.", failure));
            }
            throw UncheckedException.throwAsUncheckedException(failure);
        } else if (result instanceof DaemonUnavailable) {
            throw new DaemonInitialConnectException("The daemon we connected to was unavailable: " + ((DaemonUnavailable) result).getReason());
        } else if (result instanceof Result) {
            return (BuildActionResult) ((Result) result).getValue();
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
                objectsReceived++;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Received object #{}, type: {}", objectsReceived++, object == null ? null : object.getClass().getName());
                }

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
        findCrashLogFile(build, diagnostics).ifPresent(crashLogFile ->
            LOGGER.error("JVM crash log found: " + new ConsoleRenderer().asClickableFileUrl(crashLogFile))
        );
        throw new DaemonDisappearedException();
    }

    /**
     * <a href="https://stackoverflow.com/a/5154619/104894">See why this logic exists in this SO post.</a>
     */
    private Optional<File> findCrashLogFile(Build build, DaemonDiagnostics diagnostics) {
        String crashLogFileName = "hs_err_pid" + diagnostics.getPid() + ".log";
        List<File> candidates = new ArrayList<>();
        candidates.add(new File(build.getParameters().getCurrentDir(), crashLogFileName));
        candidates.add(new File(diagnostics.getDaemonLog().getParent(), crashLogFileName));
        findCrashLogFile(crashLogFileName).ifPresent(candidates::add);

        return candidates.stream()
            .filter(File::isFile)
            .findFirst();
    }

    private static Optional<File> findCrashLogFile(String crashLogFileName) {
        // This use case for the JavaIOTmpDir is allowed since we are looking for the crash log file.
        @SuppressWarnings("deprecation") String javaTmpDir = SystemProperties.getInstance().getJavaIoTmpDir();
        if (javaTmpDir != null && !javaTmpDir.isEmpty()) {
            return Optional.of(new File(javaTmpDir, crashLogFileName));
        }
        return Optional.empty();
    }

    private IllegalStateException invalidResponse(Object response, Build command, DaemonDiagnostics diagnostics) {
        String diagnosticsMessage = diagnostics == null ? "No diagnostics available." : diagnostics.describe();
        return new IllegalStateException(String.format(
            "Received invalid response from the daemon: '%s' is a result of a type we don't have a strategy to handle. "
                + "Earlier, '%s' request was sent to the daemon. Diagnostics:\n%s", response, command, diagnosticsMessage));
    }
}
