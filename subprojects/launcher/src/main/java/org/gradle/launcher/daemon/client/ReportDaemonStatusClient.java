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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.List;

public class ReportDaemonStatusClient {
    private static final Logger LOGGER = Logging.getLogger(DaemonClient.class);
    private final DaemonRegistry daemonRegistry;
    private final DaemonConnector connector;
    private final IdGenerator<?> idGenerator;
    private final ReportStatusDispatcher reportStatusDispatcher;
    private static final String STATUS_FORMAT = "%1$6s %2$-7s %3$s";

    public ReportDaemonStatusClient(DaemonRegistry daemonRegistry, DaemonConnector connector, IdGenerator<?> idGenerator) {
        Preconditions.checkNotNull(daemonRegistry, "DaemonRegistry must not be null");
        Preconditions.checkNotNull(connector, "DaemonConnector must not be null");
        Preconditions.checkNotNull(idGenerator, "IdGenerator must not be null");

        this.daemonRegistry = daemonRegistry;
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.reportStatusDispatcher = new ReportStatusDispatcher();
    }

    public void listAll() {
        listRunningDaemons(daemonRegistry.getAll());
    }

    @VisibleForTesting
    void listRunningDaemons(final List<DaemonInfo> daemons) {
        if (daemons.isEmpty()) {
            LOGGER.quiet(DaemonMessages.NO_DAEMONS_RUNNING);
        } else {
            LOGGER.quiet(String.format(STATUS_FORMAT, "PID", "VERSION", "STATUS"));
            for (DaemonInfo info : daemons) {
                DaemonClientConnection connection = connector.maybeConnect(info);
                if (connection != null) {
                    try {
                        Status status = reportStatusDispatcher.dispatch(connection, new ReportStatus(idGenerator.generateId(), info.getToken()));
                        if (status != null) {
                            LOGGER.quiet(String.format(STATUS_FORMAT, status.getPid(), status.getVersion(), status.getStatus()));
                        } else { // Handle failure
                            LOGGER.quiet(String.format(STATUS_FORMAT, info.getPid(), "UNKNOWN", "BROKEN"));
                        }
                    } finally {
                        connection.stop();
                    }
                }
            }
        }
    }
}
