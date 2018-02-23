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

package org.gradle.launcher.daemon.server;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;

import java.util.Date;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*;

class DaemonRegistryUpdater implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(DaemonRegistryUpdater.class);

    private final DaemonRegistry daemonRegistry;
    private final DaemonContext daemonContext;
    private final byte[] token;
    private Address connectorAddress;

    public DaemonRegistryUpdater(DaemonRegistry daemonRegistry, DaemonContext daemonContext, byte[] token) {
        this.daemonRegistry = daemonRegistry;
        this.daemonContext = daemonContext;
        this.token = token;
    }

    public void onStartActivity() {
        LOGGER.info("Marking the daemon as busy, address: {}", connectorAddress);
        try {
            daemonRegistry.markState(connectorAddress, Busy);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot mark daemon as busy because the registry is empty.");
        }
    }

    public void onCompleteActivity() {
        LOGGER.info("Marking the daemon as idle, address: {}", connectorAddress);
        try {
            daemonRegistry.markState(connectorAddress, Idle);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot mark daemon as idle because the registry is empty.");
        }
    }

    public void onCancel() {
        LOGGER.info("Marking the daemon as canceled, address: {}", connectorAddress);
        try {
            daemonRegistry.markState(connectorAddress, Canceled);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot mark daemon as canceled because the registry is empty.");
        }
    }

    public void onStart(Address connectorAddress) {
        LOGGER.info("{}{}", DaemonMessages.ADVERTISING_DAEMON, connectorAddress);
        LOGGER.debug("Advertised daemon context: {}", daemonContext);
        this.connectorAddress = connectorAddress;
        daemonRegistry.store(new DaemonInfo(connectorAddress, daemonContext, token, Busy));
    }

    public void onExpire(String reason, DaemonExpirationStatus status) {
        LOGGER.debug("Storing daemon stop event: {}", reason);
        final Date timestamp = new Date(System.currentTimeMillis());
        daemonRegistry.storeStopEvent(new DaemonStopEvent(timestamp, daemonContext.getPid(), status, reason));
    }

    public void stop() {
        LOGGER.debug("Removing our presence to clients, eg. removing this address from the registry: {}", connectorAddress);
        try {
            daemonRegistry.remove(connectorAddress);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot remove daemon from the registry because the registry is empty.");
        }
        LOGGER.debug("Address removed from registry.");
    }
}
