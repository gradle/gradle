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
package org.gradle.launcher.daemon.management.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.client.ReportStatusDispatcher;
import org.gradle.launcher.daemon.client.StopDispatcher;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.management.ManagedDaemon;
import org.gradle.launcher.daemon.management.ManagedDaemons;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.launcher.daemon.registry.DaemonStopEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link ManagedDaemons}. This is the single implementation of daemon discovery and control for the
 * current Gradle version, built directly on the {@link DaemonConnector}, the {@link DaemonRegistry}, and the
 * protocol dispatchers. There are no separate stop/status client classes; the {@code gradle --status} /
 * {@code --stop} commands, the standalone management tool, and the Tooling API shutdown path all go through
 * this type.
 */
class DefaultManagedDaemons implements ManagedDaemons {

    private static final Logger LOGGER = Logging.getLogger(DefaultManagedDaemons.class);
    private static final int STOP_TIMEOUT_SECONDS = 30;
    private static final String STATUS_FORMAT = "%1$6s %2$-8s %3$s";

    private final DaemonRegistry registry;
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final DocumentationRegistry documentationRegistry;
    private final StopDispatcher stopDispatcher = new StopDispatcher();
    private final ReportStatusDispatcher reportStatusDispatcher = new ReportStatusDispatcher();

    DefaultManagedDaemons(DaemonRegistry registry, DaemonConnector connector, IdGenerator<UUID> idGenerator, DocumentationRegistry documentationRegistry) {
        this.registry = registry;
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public List<ManagedDaemon> getDaemons() {
        List<ManagedDaemon> daemons = new ArrayList<ManagedDaemon>();
        for (DaemonInfo info : registry.getAll()) {
            daemons.add(new DefaultManagedDaemon(info, connector, idGenerator));
        }
        return daemons;
    }

    @Override
    public void stopAll() {
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

    @Override
    public void stopAllWhenIdle() {
        stopWhenIdle(new ArrayList<DaemonConnectDetails>(registry.getAll()));
    }

    @Override
    public void stopWhenIdle(Collection<? extends DaemonConnectDetails> daemons) {
        for (DaemonConnectDetails daemon : daemons) {
            DaemonClientConnection connection = connector.maybeConnect(daemon);
            if (connection == null) {
                continue;
            }
            try {
                LOGGER.debug("Requesting daemon {} stop when idle", daemon);
                stopDispatcher.dispatch(connection, new StopWhenIdle(idGenerator.generateId(), connection.getDaemon().getToken()));
                LOGGER.lifecycle("Gradle daemon stopped.");
            } finally {
                connection.stop();
            }
        }
    }

    @Override
    public void reportStatus() {
        final List<Status> statuses = new ArrayList<Status>();
        for (DaemonInfo daemon : registry.getAll()) {
            DaemonClientConnection connection = connector.maybeConnect(daemon);
            if (connection != null) {
                try {
                    Status status = reportStatusDispatcher.dispatch(connection, new ReportStatus(idGenerator.generateId(), daemon.getToken()));
                    if (status != null) {
                        statuses.add(status);
                    } else { // Handle failure
                        statuses.add(new Status(connection.getDaemon().getPid(), "UNKNOWN", "UNKNOWN"));
                    }
                } finally {
                    connection.stop();
                }
            }
        }

        final List<DaemonStopEvent> stopEvents = DaemonStopEvents.uniqueRecentDaemonStopEvents(registry.getStopEvents());
        if (statuses.isEmpty()) {
            LOGGER.quiet(DaemonMessages.NO_DAEMONS_RUNNING);
        }

        if (!(statuses.isEmpty() && stopEvents.isEmpty())) {
            LOGGER.quiet(String.format(STATUS_FORMAT, "PID", "STATUS", "INFO"));
        }

        printRunningDaemons(statuses);
        printStoppedDaemons(stopEvents);

        LOGGER.quiet("");
        LOGGER.quiet(STATUS_FOOTER + " " + documentationRegistry.getDocumentationRecommendationFor("on this", "gradle_daemon", "sec:status"));
    }

    @VisibleForTesting
    void printRunningDaemons(final List<Status> statuses) {
        for (Status status : statuses) {
            Long pid = status.getPid();
            LOGGER.quiet(String.format(STATUS_FORMAT, pid == null ? "PID unknown" : pid, status.getStatus(), status.getVersion()));
        }
    }

    @VisibleForTesting
    void printStoppedDaemons(final List<DaemonStopEvent> stopEvents) {
        for (DaemonStopEvent event : stopEvents) {
            Long pid = event.getPid();
            LOGGER.quiet(String.format(STATUS_FORMAT, pid == null ? "PID unknown" : pid, "STOPPED", "(" + event.getReason() + ")"));
        }
    }
}
