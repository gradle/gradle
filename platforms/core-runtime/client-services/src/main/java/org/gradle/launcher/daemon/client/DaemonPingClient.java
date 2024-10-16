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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Ping;
import org.gradle.launcher.daemon.protocol.Stop;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>To stop a daemon:</p>
 *
 * <ul>
 * <li>The client creates a connection to daemon.</li>
 * <li>The client sends exactly one {@link Stop} message.</li>
 * <li>The daemon sends exactly one {@link org.gradle.launcher.daemon.protocol.Result} message. It may no longer send any messages.</li>
 * <li>The client sends a {@link org.gradle.launcher.daemon.protocol.Finished} message once it has received the {@link org.gradle.launcher.daemon.protocol.Result} message.
 *     It may no longer send any messages.</li>
 * <li>The client closes the connection.</li>
 * <li>The daemon closes the connection once it has received the {@link org.gradle.launcher.daemon.protocol.Finished} message.</li>
 * </ul>
 */
public class DaemonPingClient {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final ExplainingSpec<DaemonContext> matchingContextSpec;
    private final StopDispatcher stopDispatcher;

    public DaemonPingClient(DaemonConnector connector, IdGenerator<UUID> idGenerator, ExplainingSpec<DaemonContext> matchingContextSpec) {
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.matchingContextSpec = matchingContextSpec;
        this.stopDispatcher = new StopDispatcher();
    }

    /**
     * Requests that the given daemons stop when idle. Does not block and returns before the daemons have all stopped.
     */
    public void ping(Collection<DaemonConnectDetails> daemons) {
        DaemonClientConnection conn = connector.connect(matchingContextSpec);
        if (conn == null) {
            conn = connector.startDaemon(matchingContextSpec);
        }
        stopDispatcher.dispatch(conn, new Ping(idGenerator.generateId(), conn.getDaemon().getToken()));
    }

    /**
     * Stops all daemons, blocking until all have completed.
     */
    public void stop() {
        CountdownTimer timer = Time.startCountdownTimer(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        final Set<String> seen = new HashSet<String>();

        ExplainingSpec<DaemonContext> spec = new ExplainingSpec<DaemonContext>() {
            @Override
            public String whyUnsatisfied(DaemonContext element) {
                return "already seen";
            }

            @Override
            public boolean isSatisfiedBy(DaemonContext element) {
                return !seen.contains(element.getUid());
            }
        };

        DaemonClientConnection connection = connector.maybeConnect(spec);
        if (connection == null) {
            LOGGER.lifecycle(DaemonMessages.NO_DAEMONS_RUNNING);
            return;
        }

        LOGGER.lifecycle("Stopping Daemon(s)");

        //iterate and stop all daemons
        int numStopped = 0;
        while (connection != null && !timer.hasExpired()) {
            try {
                seen.add(connection.getDaemon().getUid());
                LOGGER.debug("Requesting daemon {} stop now", connection.getDaemon());
                boolean stopped = stopDispatcher.dispatch(connection, new Stop(idGenerator.generateId(), connection.getDaemon().getToken()));
                if (stopped) {
                    numStopped++;
                }
            } finally {
                connection.stop();
            }
            connection = connector.maybeConnect(spec);
        }

        if (numStopped > 0) {
            LOGGER.lifecycle(numStopped + " Daemon" + ((numStopped > 1) ? "s" : "") + " stopped");
        }

        if (connection != null) {
            throw new GradleException(String.format("Timeout waiting for all daemons to stop. Waited %s.", timer.getElapsed()));
        }
    }
}
