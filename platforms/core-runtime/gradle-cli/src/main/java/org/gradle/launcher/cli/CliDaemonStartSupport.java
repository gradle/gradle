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

package org.gradle.launcher.cli;

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.management.DaemonStartSupport;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.api.DaemonState;

/**
 * Starts a daemon of this Gradle version and leaves it idle and available.
 *
 * <p>A daemon registers itself as busy while it starts, which reserves it for whoever started it so that
 * no other client takes it mid-handshake. That reservation is normally released when the first build
 * finishes. Here there is no build, so it is released explicitly, which is what makes the daemon
 * available to the next build rather than to this process alone.
 */
public class CliDaemonStartSupport implements DaemonStartSupport {

    private final DaemonConnector connector;
    private final DaemonRegistry registry;
    private final ExplainingSpec<DaemonContext> constraint;

    public CliDaemonStartSupport(DaemonConnector connector, DaemonRegistry registry, ExplainingSpec<DaemonContext> constraint) {
        this.connector = connector;
        this.registry = registry;
        this.constraint = constraint;
    }

    @Override
    public String startDaemon() {
        DaemonClientConnection connection = connector.startDaemon(constraint);
        try {
            registry.markState(connection.getDaemon().getAddress(), DaemonState.Idle);
            return connection.getDaemon().getUid();
        } finally {
            connection.stop();
        }
    }
}
