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
package org.gradle.launcher.daemon.client;

import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Default {@link ManagedDaemon}, backed by a registry entry and the daemon protocol. Each operation opens a
 * fresh loopback connection via the {@link DaemonConnector}, dispatches a single command, and closes it.
 */
@NullMarked
class DefaultManagedDaemon implements ManagedDaemon {

    private final DaemonInfo info;
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final StopDispatcher stopDispatcher = new StopDispatcher();
    private final ReportStatusDispatcher reportStatusDispatcher = new ReportStatusDispatcher();

    DefaultManagedDaemon(DaemonInfo info, DaemonConnector connector, IdGenerator<UUID> idGenerator) {
        this.info = info;
        this.connector = connector;
        this.idGenerator = idGenerator;
    }

    @Nullable
    @Override
    public Long getPid() {
        return info.getPid();
    }

    @Nullable
    @Override
    public Status getStatus() {
        DaemonClientConnection connection = connector.maybeConnect(info);
        if (connection == null) {
            return null;
        }
        try {
            return reportStatusDispatcher.dispatch(connection, new ReportStatus(idGenerator.generateId(), connection.getDaemon().getToken()));
        } finally {
            connection.stop();
        }
    }

    @Override
    public void stop() {
        DaemonClientConnection connection = connector.maybeConnect(info);
        if (connection == null) {
            return;
        }
        try {
            stopDispatcher.dispatch(connection, new Stop(idGenerator.generateId(), connection.getDaemon().getToken()));
        } finally {
            connection.stop();
        }
    }

    @Override
    public void stopWhenIdle() {
        DaemonClientConnection connection = connector.maybeConnect(info);
        if (connection == null) {
            return;
        }
        try {
            stopDispatcher.dispatch(connection, new StopWhenIdle(idGenerator.generateId(), connection.getDaemon().getToken()));
        } finally {
            connection.stop();
        }
    }
}
