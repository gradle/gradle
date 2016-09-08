/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.launcher.daemon.registry.DaemonStopEvents;

import java.util.List;

public class ReportDaemonStatusClient {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();
    private final DaemonRegistry daemonRegistry;
    private final DaemonConnector connector;
    private final IdGenerator<?> idGenerator;
    private final ReportStatusDispatcher reportStatusDispatcher;
    private final DocumentationRegistry documentationRegistry;
    public static final String STATUS_FOOTER = "Only Daemons for the current Gradle version are displayed.";
    private static final String STATUS_FORMAT = "%1$6s %2$-8s %3$s";

    public ReportDaemonStatusClient(DaemonRegistry daemonRegistry, DaemonConnector connector, IdGenerator<?> idGenerator, DocumentationRegistry documentationRegistry) {
        Preconditions.checkNotNull(daemonRegistry, "DaemonRegistry must not be null");
        Preconditions.checkNotNull(connector, "DaemonConnector must not be null");
        Preconditions.checkNotNull(idGenerator, "IdGenerator must not be null");
        Preconditions.checkNotNull(documentationRegistry, "DocumentationRegistry must not be null");

        this.daemonRegistry = daemonRegistry;
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.reportStatusDispatcher = new ReportStatusDispatcher();
        this.documentationRegistry = documentationRegistry;
    }

    public void listAll() {
        final List<DaemonInfo> daemons = daemonRegistry.getAll();
        final List<Status> statuses = Lists.newArrayList();
        for (DaemonInfo daemon : daemons) {
            DaemonClientConnection connection = connector.maybeConnect(daemon);
            if (connection != null) {
                try {
                    final ReportStatus statusCommand = new ReportStatus(idGenerator.generateId(), daemon.getToken());
                    final Status status = reportStatusDispatcher.dispatch(connection, statusCommand);
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

        final List<DaemonStopEvent> stopEvents = DaemonStopEvents.uniqueRecentDaemonStopEvents(daemonRegistry.getStopEvents());
        if (statuses.isEmpty()) {
            LOGGER.quiet(DaemonMessages.NO_DAEMONS_RUNNING);
        }

        if (!(statuses.isEmpty() && stopEvents.isEmpty())) {
            LOGGER.quiet(String.format(STATUS_FORMAT, "PID", "STATUS", "INFO"));
        }

        printRunningDaemons(statuses);
        printStoppedDaemons(stopEvents);

        LOGGER.quiet("");

        LOGGER.quiet(STATUS_FOOTER + " See " + documentationRegistry.getDocumentationFor("gradle_daemon", "sec:status"));
    }

    @VisibleForTesting
    void printRunningDaemons(final List<Status> statuses) {
        if (!statuses.isEmpty()) {
            for(Status status : statuses) {
                Long pid = status.getPid();
                LOGGER.quiet(String.format(STATUS_FORMAT, pid == null ? "PID unknown" : pid, status.getStatus(), status.getVersion()));
            }
        }
    }

    @VisibleForTesting
    void printStoppedDaemons(final List<DaemonStopEvent> stopEvents) {
        if (!stopEvents.isEmpty()) {
            for(DaemonStopEvent event : stopEvents) {
                Long pid = event.getPid();
                LOGGER.quiet(String.format(STATUS_FORMAT, pid == null ? "PID unknown" : pid, "STOPPED", "(" + event.getReason() + ")"));
            }
        }
    }
}
