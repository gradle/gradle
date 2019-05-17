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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.internal.specs.ExplainingSpecs;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.remote.internal.ConnectException;
import org.gradle.internal.remote.internal.OutgoingConnector;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.Serializers;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.launcher.daemon.registry.DaemonStopEvents;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Canceled;
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle;

/**
 * Provides the mechanics of connecting to a daemon, starting one via a given runnable if no suitable daemons are already available.
 */
public class DefaultDaemonConnector implements DaemonConnector {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonConnector.class);
    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    public static final int CANCELED_WAIT_TIMEOUT = 3000;
    private final DaemonRegistry daemonRegistry;
    protected final OutgoingConnector connector;
    private final DaemonStarter daemonStarter;
    private final DaemonStartListener startListener;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Serializer<Message> serializer;
    private long connectTimeout = DefaultDaemonConnector.DEFAULT_CONNECT_TIMEOUT;

    public DefaultDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector connector, DaemonStarter daemonStarter, DaemonStartListener startListener, ProgressLoggerFactory progressLoggerFactory, Serializer<Message> serializer) {
        this.serializer = serializer;
        Preconditions.checkNotNull(daemonRegistry);
        Preconditions.checkNotNull(connector);
        Preconditions.checkNotNull(daemonStarter);
        Preconditions.checkNotNull(startListener);
        Preconditions.checkNotNull(progressLoggerFactory);

        this.daemonRegistry = daemonRegistry;
        this.connector = connector;
        this.daemonStarter = daemonStarter;
        this.startListener = startListener;
        this.progressLoggerFactory = progressLoggerFactory;
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

    @Override
    public DaemonClientConnection maybeConnect(ExplainingSpec<DaemonContext> constraint) {
        return findConnection(getCompatibleDaemons(daemonRegistry.getAll(), constraint));
    }

    @Override
    public DaemonClientConnection maybeConnect(DaemonConnectDetails daemon) {
        try {
            return connectToDaemon(daemon, new CleanupOnStaleAddress(daemon, true));
        } catch (ConnectException e) {
            LOGGER.debug("Cannot connect to daemon {} due to {}. Ignoring.", daemon, e);
        }
        return null;
    }

    @Override
    public DaemonClientConnection connect(ExplainingSpec<DaemonContext> constraint) {
        final Pair<Collection<DaemonInfo>, Collection<DaemonInfo>> idleBusy = partitionByState(daemonRegistry.getAll(), Idle);
        final Collection<DaemonInfo> idleDaemons = idleBusy.getLeft();
        final Collection<DaemonInfo> busyDaemons = idleBusy.getRight();

        // Check to see if there are any compatible idle daemons
        DaemonClientConnection connection = connectToIdleDaemon(idleDaemons, constraint);
        if (connection != null) {
            return connection;
        }

        // Check to see if there are any compatible canceled daemons and wait to see if one becomes idle
        connection = connectToCanceledDaemon(busyDaemons, constraint);
        if (connection != null) {
            return connection;
        }

        // No compatible daemons available - start a new daemon
        handleStopEvents(idleDaemons, busyDaemons);
        return startDaemon(constraint);
    }

    private void handleStopEvents(Collection<DaemonInfo> idleDaemons, Collection<DaemonInfo> busyDaemons) {
        final List<DaemonStopEvent> stopEvents = daemonRegistry.getStopEvents();

        // Clean up old stop events
        daemonRegistry.removeStopEvents(DaemonStopEvents.oldStopEvents(stopEvents));

        final List<DaemonStopEvent> recentStopEvents = DaemonStopEvents.uniqueRecentDaemonStopEvents(stopEvents);
        for (DaemonStopEvent stopEvent : recentStopEvents) {
            Long pid = stopEvent.getPid();
            LOGGER.info("Previous Daemon (" + (pid == null ? "PID unknown" : pid) + ") stopped at " + stopEvent.getTimestamp() + " " + stopEvent.getReason());
        }

        LOGGER.lifecycle(DaemonStartupMessage.generate(busyDaemons.size(), idleDaemons.size(), recentStopEvents.size()));
    }

    private DaemonClientConnection connectToIdleDaemon(Collection<DaemonInfo> idleDaemons, ExplainingSpec<DaemonContext> constraint) {
        final List<DaemonInfo> compatibleIdleDaemons = getCompatibleDaemons(idleDaemons, constraint);
        return findConnection(compatibleIdleDaemons);
    }

    private DaemonClientConnection connectToCanceledDaemon(Collection<DaemonInfo> busyDaemons, ExplainingSpec<DaemonContext> constraint) {
        DaemonClientConnection connection = null;
        final Pair<Collection<DaemonInfo>, Collection<DaemonInfo>> canceledBusy = partitionByState(busyDaemons, Canceled);
        final Collection<DaemonInfo> compatibleCanceledDaemons = getCompatibleDaemons(canceledBusy.getLeft(), constraint);
        if (!compatibleCanceledDaemons.isEmpty()) {
            LOGGER.info(DaemonMessages.WAITING_ON_CANCELED);
            CountdownTimer timer = Time.startCountdownTimer(CANCELED_WAIT_TIMEOUT);
            while (connection == null && !timer.hasExpired()) {
                try {
                    sleep(200);
                    connection = connectToIdleDaemon(daemonRegistry.getIdle(), constraint);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
        return connection;
    }

    private Pair<Collection<DaemonInfo>, Collection<DaemonInfo>> partitionByState(final Collection<DaemonInfo> daemons, final DaemonStateControl.State state) {
        return CollectionUtils.partition(daemons, new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() == state;
            }
        });
    }

    private List<DaemonInfo> getCompatibleDaemons(Iterable<DaemonInfo> daemons, ExplainingSpec<DaemonContext> constraint) {
        List<DaemonInfo> compatibleDaemons = new LinkedList<DaemonInfo>();
        for (DaemonInfo daemon : daemons) {
            if (constraint.isSatisfiedBy(daemon.getContext())) {
                compatibleDaemons.add(daemon);
            } else {
                LOGGER.info("Found daemon {} however its context does not match the desired criteria.\n"
                    + constraint.whyUnsatisfied(daemon.getContext()) + "\n"
                    + "  Looking for a different daemon...", daemon);
            }
        }
        return compatibleDaemons;
    }

    private DaemonClientConnection findConnection(List<DaemonInfo> compatibleDaemons) {
        for (DaemonInfo daemon : compatibleDaemons) {
            try {
                return connectToDaemon(daemon, new CleanupOnStaleAddress(daemon, true));
            } catch (ConnectException e) {
                LOGGER.debug("Cannot connect to daemon {} due to {}. Trying a different daemon...", daemon, e);
            }
        }
        return null;
    }

    @Override
    public DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint) {
        return doStartDaemon(constraint, false);
    }

    private DaemonClientConnection doStartDaemon(ExplainingSpec<DaemonContext> constraint, boolean singleRun) {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultDaemonConnector.class)
            .start("Starting Gradle Daemon", "Starting Daemon");
        final DaemonStartupInfo startupInfo = daemonStarter.startDaemon(singleRun);
        LOGGER.debug("Started Gradle daemon {}", startupInfo);
        CountdownTimer timer = Time.startCountdownTimer(connectTimeout);
        try {
            do {
                DaemonClientConnection daemonConnection = connectToDaemonWithId(startupInfo, constraint);
                if (daemonConnection != null) {
                    startListener.daemonStarted(daemonConnection.getDaemon());
                    return daemonConnection;
                }
                try {
                    sleep(200L);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            } while (!timer.hasExpired());
        } finally {
            progressLogger.completed();
        }

        throw new DaemonConnectionException("Timeout waiting to connect to the Gradle daemon.\n" + startupInfo.describe());
    }

    @Override
    public DaemonClientConnection startSingleUseDaemon() {
        return doStartDaemon(ExplainingSpecs.<DaemonContext>satisfyAll(), true);
    }

    private DaemonClientConnection connectToDaemonWithId(DaemonStartupInfo daemon, ExplainingSpec<DaemonContext> constraint) throws ConnectException {
        // Look for 'our' daemon among the busy daemons - a daemon will start in busy state so that nobody else will grab it.
        for (DaemonInfo daemonInfo : daemonRegistry.getNotIdle()) {
            if (daemonInfo.getUid().equals(daemon.getUid())) {
                try {
                    if (!constraint.isSatisfiedBy(daemonInfo.getContext())) {
                        throw new DaemonConnectionException("The newly created daemon process has a different context than expected."
                            + "\nIt won't be possible to reconnect to this daemon. Context mismatch: "
                            + "\n" + constraint.whyUnsatisfied(daemonInfo.getContext()));
                    }
                    return connectToDaemon(daemonInfo, new CleanupOnStaleAddress(daemonInfo, false));
                } catch (ConnectException e) {
                    throw new DaemonConnectionException("Could not connect to the Gradle daemon.\n" + daemon.describe(), e);
                }
            }
        }
        return null;
    }

    private DaemonClientConnection connectToDaemon(DaemonConnectDetails daemon, DaemonClientConnection.StaleAddressDetector staleAddressDetector) throws ConnectException {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultDaemonConnector.class)
            .start("Connecting to Gradle Daemon", "Connecting to Daemon");
        RemoteConnection<Message> connection;
        try {
            connection = connector.connect(daemon.getAddress()).create(Serializers.stateful(serializer));
        } catch (ConnectException e) {
            staleAddressDetector.maybeStaleAddress(e);
            throw e;
        } finally {
            progressLogger.completed();
        }
        return new DaemonClientConnection(connection, daemon, staleAddressDetector);
    }

    private class CleanupOnStaleAddress implements DaemonClientConnection.StaleAddressDetector {
        private final DaemonConnectDetails daemon;
        private final boolean exposeAsStale;

        public CleanupOnStaleAddress(DaemonConnectDetails daemon, boolean exposeAsStale) {
            this.daemon = daemon;
            this.exposeAsStale = exposeAsStale;
        }

        @Override
        public boolean maybeStaleAddress(Exception failure) {
            LOGGER.info("{}{}", DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE, daemon);
            final Date timestamp = new Date(System.currentTimeMillis());
            final DaemonStopEvent stopEvent = new DaemonStopEvent(timestamp, daemon.getPid(), null, "by user or operating system");
            daemonRegistry.storeStopEvent(stopEvent);
            daemonRegistry.remove(daemon.getAddress());
            return exposeAsStale;
        }
    }
}
