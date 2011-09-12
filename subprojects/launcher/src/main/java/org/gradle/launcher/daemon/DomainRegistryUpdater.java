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

package org.gradle.launcher.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.messaging.remote.Address;

/**
* @author: Szczepan Faber, created at: 9/12/11
*/
class DomainRegistryUpdater implements CompletionHandler.ActivityListener {

    private static final Logger LOGGER = Logging.getLogger(DomainRegistryUpdater.class);

    private final DaemonRegistry daemonRegistry;
    private final Address connectorAddress;

    public DomainRegistryUpdater(DaemonRegistry daemonRegistry, Address connectorAddress) {
        this.daemonRegistry = daemonRegistry;
        this.connectorAddress = connectorAddress;
    }

    public void onStartActivity(CompletionAware completionAware) {
        LOGGER.info("Marking the daemon as busy, address: " + connectorAddress);
        if (completionAware.isStopped()) {
            LOGGER.info("The daemon was stopped so it's no longer in the registry. We will not update the registry.");
            return;
        }
        try {
            daemonRegistry.markBusy(connectorAddress);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot mark daemon as busy because the registry is empty.");
        }
    }

    public void onCompleteActivity(CompletionAware completionAware) {
        LOGGER.info("Marking the daemon as idle, address: " + connectorAddress);
        if (completionAware.isStopped()) {
            LOGGER.info("The daemon was stopped so it's no longer in the registry. We will not update the registry.");
            return;
        }
        try {
            daemonRegistry.markIdle(connectorAddress);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot mark daemon as idle because the registry is empty.");
        }
    }

    public void onStart() {
        LOGGER.info("Advertising the daemon address to the clients: " + connectorAddress);
        daemonRegistry.store(connectorAddress);
    }

    public void onStop() {
        LOGGER.info("Removing our presence to clients, eg. removing this address from the registry: " + connectorAddress);
        try {
            daemonRegistry.remove(connectorAddress);
        } catch (DaemonRegistry.EmptyRegistryException e) {
            LOGGER.warn("Cannot remove daemon from the registry because the registry is empty.");
        }
        LOGGER.info("Address removed from registry.");
    }
}
