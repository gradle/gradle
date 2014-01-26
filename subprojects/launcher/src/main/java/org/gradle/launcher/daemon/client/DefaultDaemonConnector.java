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

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.OutgoingConnector;

import java.util.List;

/**
 * Provides the mechanics of connecting to a daemon, starting one via a given runnable if no suitable daemons are already available.
 */
public class DefaultDaemonConnector implements DaemonConnector {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonConnector.class);
    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    private final DaemonRegistry daemonRegistry;
    protected final OutgoingConnector connector;
    private final DaemonStarter daemonStarter;
    private long connectTimeout = DefaultDaemonConnector.DEFAULT_CONNECT_TIMEOUT;

    public DefaultDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector connector, DaemonStarter daemonStarter) {
        this.daemonRegistry = daemonRegistry;
        this.connector = connector;
        this.daemonStarter = daemonStarter;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public DaemonRegistry getDaemonRegistry() {
        return daemonRegistry;
    }

    public DaemonClientConnection maybeConnect(ExplainingSpec<DaemonContext> constraint) {
        return findConnection(daemonRegistry.getAll(), constraint);
    }

    public DaemonClientConnection connect(ExplainingSpec<DaemonContext> constraint) {
        DaemonClientConnection connection = findConnection(daemonRegistry.getIdle(), constraint);
        if (connection != null) {
            return connection;
        }

        return startDaemon(constraint);
    }

    private DaemonClientConnection findConnection(List<DaemonInfo> daemonInfos, ExplainingSpec<DaemonContext> constraint) {
        for (final DaemonInfo daemonInfo : daemonInfos) {
            if (!constraint.isSatisfiedBy(daemonInfo.getContext())) {
                LOGGER.debug("Found daemon (address: {}, idle: {}) however its context does not match the desired criteria.\n"
                        + constraint.whyUnsatisfied(daemonInfo.getContext()) + "\n"
                        + "  Looking for a different daemon...", daemonInfo.getAddress(), daemonInfo.isIdle());
                continue;
            }

            try {
                return connectToDaemon(daemonInfo, new CleanupOnStaleAddress(daemonInfo, true));
            } catch (ConnectException e) {
                LOGGER.debug("Cannot connect to the daemon at " + daemonInfo.getAddress() + " due to " + e + ". Trying a different daemon...");
            }
        }
        return null;
    }

    public DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint) {
        LOGGER.info("Starting Gradle daemon");
        final DaemonStartupInfo startupInfo = daemonStarter.startDaemon();
        LOGGER.debug("Started Gradle Daemon: {}", startupInfo);
        long expiry = System.currentTimeMillis() + connectTimeout;
        do {
            DaemonClientConnection daemonConnection = connectToDaemonWithId(startupInfo, constraint);
            if (daemonConnection != null) {
                return daemonConnection;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } while (System.currentTimeMillis() < expiry);

        throw new DaemonConnectionException("Timeout waiting to connect to the Gradle daemon.\n" + startupInfo.describe());
    }

    private DaemonClientConnection connectToDaemonWithId(DaemonStartupInfo startupInfo, ExplainingSpec<DaemonContext> constraint) throws ConnectException {
        // Look for 'our' daemon among the busy daemons - a daemon will start in busy state so that nobody else will grab it.
        for (DaemonInfo daemonInfo : daemonRegistry.getBusy()) {
            if (daemonInfo.getContext().getUid().equals(startupInfo.getUid())) {
                try {
                    if (!constraint.isSatisfiedBy(daemonInfo.getContext())) {
                        throw new DaemonConnectionException("The newly created daemon process has a different context than expected."
                                + "\nIt won't be possible to reconnect to this daemon. Context mismatch: "
                                + "\n" + constraint.whyUnsatisfied(daemonInfo.getContext()));
                    }
                    return connectToDaemon(daemonInfo, new CleanupOnStaleAddress(daemonInfo, false));
                } catch (ConnectException e) {
                    throw new DaemonConnectionException("Could not connect to the Gradle daemon.\n" + startupInfo.describe(), e);
                }
            }
        }
        return null;
    }

    private DaemonClientConnection connectToDaemon(DaemonInfo daemonInfo, DaemonClientConnection.StaleAddressDetector staleAddressDetector) throws ConnectException {
        Connection<Object> connection;
        try {
            connection = connector.connect(daemonInfo.getAddress()).create(getClass().getClassLoader());
        } catch (ConnectException e) {
            staleAddressDetector.maybeStaleAddress(e);
            throw e;
        }
        return new DaemonClientConnection(connection, daemonInfo.getContext().getUid(), staleAddressDetector);
    }

    private class CleanupOnStaleAddress implements DaemonClientConnection.StaleAddressDetector {
        private final DaemonInfo daemonInfo;
        private final boolean exposeAsStale;

        public CleanupOnStaleAddress(DaemonInfo daemonInfo, boolean exposeAsStale) {
            this.daemonInfo = daemonInfo;
            this.exposeAsStale = exposeAsStale;
        }

        public boolean maybeStaleAddress(Exception failure) {
            LOGGER.info(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE + daemonInfo);
            daemonRegistry.remove(daemonInfo.getAddress());
            return exposeAsStale;
        }
    }
}
