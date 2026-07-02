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
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link ManagedDaemons}.
 *
 * <p>The per-daemon handles are backed directly by the {@link DaemonConnector} and the protocol dispatchers.
 * The bulk operations reuse the existing, battle-tested clients so their exact behaviour and console output
 * (the stop timeout loop, the status table and footer) are preserved unchanged.
 */
@NullMarked
public class DefaultManagedDaemons implements ManagedDaemons {

    private final DaemonRegistry registry;
    private final DaemonConnector connector;
    private final IdGenerator<UUID> idGenerator;
    private final DaemonStopClient stopClient;
    private final ReportDaemonStatusClient statusClient;

    public DefaultManagedDaemons(DaemonRegistry registry, DaemonConnector connector, IdGenerator<UUID> idGenerator,
                                 DaemonStopClient stopClient, ReportDaemonStatusClient statusClient) {
        this.registry = registry;
        this.connector = connector;
        this.idGenerator = idGenerator;
        this.stopClient = stopClient;
        this.statusClient = statusClient;
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
        stopClient.stop();
    }

    @Override
    public void stopAllWhenIdle() {
        stopClient.gracefulStop(new ArrayList<DaemonConnectDetails>(registry.getAll()));
    }

    @Override
    public void reportStatus() {
        statusClient.listAll();
    }
}
