/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.connection;

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Build;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The {@link DaemonBuildExecuter} used for normal builds: it tries to connect to an existing idle and compatible
 * daemon, and starts a new one if none is available.
 */
@NullMarked
public class DefaultDaemonBuildExecuter extends AbstractDaemonBuildExecuter {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonBuildExecuter.class);

    private final ExplainingSpec<DaemonContext> compatibilitySpec;

    public DefaultDaemonBuildExecuter(
        DaemonConnector connector,
        OutputEventListener outputEventListener,
        ExplainingSpec<DaemonContext> compatibilitySpec,
        InputStream buildStandardInput,
        GlobalUserInputReceiver userInput,
        IdGenerator<UUID> idGenerator,
        ProcessEnvironment processEnvironment
    ) {
        super(connector, outputEventListener, buildStandardInput, userInput, idGenerator, processEnvironment);
        this.compatibilitySpec = compatibilitySpec;
    }

    @Override
    public DaemonBuildResult execute(DaemonBuildRequest request) {
        JvmBuildRequest jvmRequest = requireJvmRequest(request);
        UUID buildId = nextBuildId();
        List<DaemonInitialConnectException> accumulatedExceptions = new ArrayList<>();

        LOGGER.debug("Executing build {} in daemon client {pid={}}", buildId, getProcessEnvironment().maybeGetPid());

        // Attempt to connect to an existing idle and compatible daemon
        int saneNumberOfAttempts = 100; //is it sane enough?
        for (int i = 1; i < saneNumberOfAttempts; i++) {
            final DaemonClientConnection connection = getConnector().connect(compatibilitySpec);
            // No existing, compatible daemon is available to try
            if (connection == null) {
                break;
            }
            // Compatible daemon was found, try it
            try {
                Build build = newBuild(buildId, connection, jvmRequest);
                return new JvmBuildResult(executeBuild(build, connection, request.getCancellationToken(), request.getEventConsumer()));
            } catch (DaemonInitialConnectException e) {
                // this exception means that we want to try again.
                LOGGER.debug("{}, Trying a different daemon...", e.getMessage());
                accumulatedExceptions.add(e);
            } finally {
                connection.stop();
            }
        }

        // No existing daemon was usable, so start a new one and try it once
        final DaemonClientConnection connection = getConnector().startDaemon(compatibilitySpec);
        try {
            Build build = newBuild(buildId, connection, jvmRequest);
            return new JvmBuildResult(executeBuild(build, connection, request.getCancellationToken(), request.getEventConsumer()));
        } catch (DaemonInitialConnectException e) {
            // This means we could not connect to the daemon we just started.  fail and don't try again
            accumulatedExceptions.add(e);
            throw new NoUsableDaemonFoundException("A new daemon was started but could not be connected to. This is unexpected.\n" +
                "diagnostics: " + connection.getDaemon(),
                accumulatedExceptions);
        } finally {
            connection.stop();
        }
    }
}
