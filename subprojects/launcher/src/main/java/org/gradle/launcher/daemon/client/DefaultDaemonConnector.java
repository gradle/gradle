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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.remote.internal.ConnectException;
import org.gradle.internal.remote.internal.OutgoingConnector;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.serialize.Serializers;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Provides the mechanics of connecting to a daemon, starting one via a given runnable if no suitable daemons are already available.
 */
public class DefaultDaemonConnector implements DaemonConnector {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonConnector.class);
    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    public static final String STARTING_DAEMON_MESSAGE = "Starting a new Gradle Daemon for this build.";
    public static final String SUBSEQUENT_BUILDS_FASTER_MESSAGE = "Subsequent builds will be faster.";
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();
    private final DaemonRegistry daemonRegistry;
    protected final OutgoingConnector connector;
    private final DaemonStarter daemonStarter;
    private final DaemonStartListener startListener;
    private long connectTimeout = DefaultDaemonConnector.DEFAULT_CONNECT_TIMEOUT;

    public DefaultDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector connector, DaemonStarter daemonStarter, DaemonStartListener startListener) {
        this.daemonRegistry = daemonRegistry;
        this.connector = connector;
        this.daemonStarter = daemonStarter;
        this.startListener = startListener;
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
        return findConnection(getCompatibleDaemons(daemonRegistry.getAll(), constraint));
    }

    public DaemonClientConnection maybeConnect(DaemonConnectDetails daemon) {
        try {
            return connectToDaemon(daemon, new CleanupOnStaleAddress(daemon, true));
        } catch (ConnectException e) {
            LOGGER.debug("Cannot connect to daemon {} due to {}. Ignoring.", daemon, e);
        }
        return null;
    }

    public DaemonClientConnection connect(ExplainingSpec<DaemonContext> constraint) {
        List<DaemonInfo> compatibleIdleDaemons = getCompatibleDaemons(daemonRegistry.getIdle(), constraint);
        DaemonClientConnection connection = findConnection(compatibleIdleDaemons);
        if (connection != null) {
            return connection;
        }

        final int numBusy = daemonRegistry.getBusy().size();
        final int numIncompatible = daemonRegistry.getIdle().size();
        final List<DaemonStopEvent> stopEvents = daemonRegistry.getStopEvents();
        LOGGER.lifecycle(generateStartingMessage(numBusy, numIncompatible, stopEvents));
        daemonRegistry.clearStopEvents();

        return startDaemon(constraint);
    }

    @VisibleForTesting
    String generateStartingMessage(final int numBusy, final int numIncompatible, final List<DaemonStopEvent> stopEvents) {
        final List<String> reasons = Lists.newArrayList(STARTING_DAEMON_MESSAGE);
        if (numBusy > 0) {
            reasons.add(numBusy + " " + (numBusy > 1 ? "are" : "is") + " busy");
        }
        if (numIncompatible > 0) {
            reasons.add(numIncompatible + " " + (numIncompatible > 1 ? "are" : "is") + " incompatible");
        }

        if (stopEvents.size() > 0) {
            // REVIEWME: seems like this should be more concise/elegant
            Map<String, Integer> countByReason = new HashMap<String, Integer>();
            for (DaemonStopEvent event : stopEvents) {
                final String reason = event.getReason();
                Integer count = countByReason.get(reason) == null ? 0 : countByReason.get(reason);
                countByReason.put(reason, count + 1);
            }

            for (Map.Entry<String, Integer> entry : countByReason.entrySet()) {
                Integer numStopped = entry.getValue();
                reasons.add(numStopped + " " + (numStopped > 1 ? "were" : "was") + " stopped because " + entry.getKey());
            }
        }

        final String startingDaemonMessage = Joiner.on(LINE_SEPARATOR + " - ").skipNulls().join(reasons);
        return startingDaemonMessage + LINE_SEPARATOR + SUBSEQUENT_BUILDS_FASTER_MESSAGE;
    }

    private List<DaemonInfo> getCompatibleDaemons(List<DaemonInfo> daemons, ExplainingSpec<DaemonContext> constraint) {
        List<DaemonInfo> compatibleDaemons = new LinkedList<DaemonInfo>();
        for (DaemonInfo daemon : daemons) {
            if (constraint.isSatisfiedBy(daemon.getContext())) {
                compatibleDaemons.add(daemon);
            } else {
                LOGGER.debug("Found daemon {} however its context does not match the desired criteria.\n"
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

    public DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint) {
        final DaemonStartupInfo startupInfo = daemonStarter.startDaemon();
        LOGGER.debug("Started Gradle daemon {}", startupInfo);
        long expiry = System.currentTimeMillis() + connectTimeout;
        do {
            DaemonClientConnection daemonConnection = connectToDaemonWithId(startupInfo, constraint);
            if (daemonConnection != null) {
                startListener.daemonStarted(daemonConnection.getDaemon());
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

    private DaemonClientConnection connectToDaemonWithId(DaemonStartupInfo daemon, ExplainingSpec<DaemonContext> constraint) throws ConnectException {
        // Look for 'our' daemon among the busy daemons - a daemon will start in busy state so that nobody else will grab it.
        for (DaemonInfo daemonInfo : daemonRegistry.getBusy()) {
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
        RemoteConnection<Message> connection;
        try {
            connection = connector.connect(daemon.getAddress()).create(Serializers.stateful(DaemonMessageSerializer.create()));
        } catch (ConnectException e) {
            staleAddressDetector.maybeStaleAddress(e);
            throw e;
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

        public boolean maybeStaleAddress(Exception failure) {
            LOGGER.info("{}{}", DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE, daemon);
            final Date timestamp = new Date(System.currentTimeMillis());
            daemonRegistry.storeStopEvent(new DaemonStopEvent(timestamp, "daemon was terminated"));
            daemonRegistry.remove(daemon.getAddress());
            return exposeAsStale;
        }
    }
}
