/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.internal.specs.ExplainingSpecs;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonInstanceDetails;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>To stop a daemon:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link org.gradle.launcher.daemon.protocol.Stop} message.</li>
 * <li>The daemon sends exactly one {@link org.gradle.launcher.daemon.protocol.Result} message. It may no longer send any messages.</li>
 * <li>The client sends a {@link org.gradle.launcher.daemon.protocol.Finished} message once it has received the {@link org.gradle.launcher.daemon.protocol.Result} message.
 *     It may no longer send any messages.</li>
 * <li>The client closes the connection.</li>
 * <li>The daemon closes the connection once it has received the {@link org.gradle.launcher.daemon.protocol.Finished} message.</li>
 * </ul>
 */
public class DaemonStopClient {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private final DaemonConnector connector;
    private final IdGenerator<?> idGenerator;
    private final ExplainingSpec<DaemonContext> compatibilitySpec = ExplainingSpecs.satisfyAll();
    private final StopDispatcher stopDispatcher;

    public DaemonStopClient(DaemonConnector connector, IdGenerator<?> idGenerator) {
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.stopDispatcher = new StopDispatcher();
    }

    /**
     * Requests that the given daemons stop when idle. Does not block and returns before the daemons have all stopped.
     */
    public void gracefulStop(Collection<DaemonInstanceDetails> daemons) {
        for (DaemonInstanceDetails daemon : daemons) {
            DaemonClientConnection connection = connector.maybeConnect(daemon);
            if (connection == null) {
                continue;
            }
            try {
                LOGGER.debug("Requesting daemon {} stop when idle", daemon);
                stopDispatcher.dispatch(connection, new StopWhenIdle(idGenerator.generateId()));
                LOGGER.lifecycle("Gradle daemon stopped.");
            } finally {
                connection.stop();
            }
        }
    }

    /**
     * Stops all daemons, blocking until all have completed.
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
                if (stopped.add(connection.getDaemon().getUid())) {
                    LOGGER.debug("Requesting daemon {} stop now", connection.getDaemon());
                    stopDispatcher.dispatch(connection, new Stop(idGenerator.generateId()));
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
}
