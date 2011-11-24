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

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.OutgoingConnector;
import org.gradle.util.UncheckedException;

import java.util.List;

/**
 * Provides the mechanics of connecting to a daemon, starting one via a given runnable if no suitable daemons are already available.
 */
public class DefaultDaemonConnector implements DaemonConnector {

    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonConnector.class);
    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    private final DaemonRegistry daemonRegistry;
    private final Spec<DaemonContext> contextCompatibilitySpec;
    private final OutgoingConnector<Object> connector;
    private final Runnable daemonStarter;

    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    public DefaultDaemonConnector(DaemonRegistry daemonRegistry, Spec<DaemonContext> contextCompatibilitySpec, OutgoingConnector<Object> connector, Runnable daemonStarter) {
        this.daemonRegistry = daemonRegistry;
        this.contextCompatibilitySpec = contextCompatibilitySpec;
        this.connector = connector;
        this.daemonStarter = daemonStarter;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public DaemonConnection maybeConnect() {
        return findConnection(daemonRegistry.getAll());
    }

    private DaemonConnection findConnection(List<DaemonInfo> daemonInfos) {
        for (DaemonInfo daemonInfo : daemonInfos) {
            if (!contextCompatibilitySpec.isSatisfiedBy(daemonInfo.getContext())) {
                continue;
            }

            try {
                return new DaemonConnection(connector.connect(daemonInfo.getAddress()), daemonInfo.getPassword());
            } catch (ConnectException e) {
                //this means the daemon died without removing its address from the registry
                //we can safely remove this address now
                LOGGER.debug("We cannot connect to the daemon at " + daemonInfo.getAddress() + " due to " + e + ". "
                        + "We will not remove this daemon from the registry because the connection issue may have been temporary.");
                //TODO it might be good to store in the registry the number of failed attempts to connect to the deamon
                //if the number is high we may decide to remove the daemon from the registry
                //daemonRegistry.remove(address);
            }
        }
        return null;
    }

    public DaemonConnection connect() {
        DaemonConnection connection = findConnection(daemonRegistry.getIdle());
        if (connection != null) {
            return connection;
        }

        LOGGER.info("Starting Gradle daemon");
        daemonStarter.run();
        long expiry = System.currentTimeMillis() + connectTimeout;
        do {
            connection = findConnection(daemonRegistry.getIdle());
            if (connection != null) {
                return connection;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        } while (System.currentTimeMillis() < expiry);

        throw new GradleException("Timeout waiting to connect to Gradle daemon.");
    }

    public DaemonRegistry getDaemonRegistry() {
        return daemonRegistry;
    }
}
