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

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.util.UncheckedException;

import java.util.Date;
import java.util.List;

/**
 * Provides the general connection mechanics of connecting to a daemon, leaving implementations
 * to define how new daemons should be created if needed.
 * 
 * Subclassing instead of delegation with regard to creating new daemons seems more appropriate
 * as the way that new daemons are launched is likely to be coupled to the DaemonRegistry implementation.
 */
abstract public class AbstractDaemonConnector<T extends DaemonRegistry> implements DaemonConnector {

    private static final Logger LOGGER = Logging.getLogger(AbstractDaemonConnector.class);

    private final T daemonRegistry;

    protected AbstractDaemonConnector(T daemonRegistry) {
        this.daemonRegistry = daemonRegistry;
    }

    public Connection<Object> maybeConnect() {
        return findConnection(daemonRegistry.getAll());
    }

    private Connection<Object> findConnection(List<DaemonStatus> statuses) {
        for (DaemonStatus status : statuses) {
            Address address = status.getAddress();
            try {
                return new TcpOutgoingConnector<Object>(new DefaultMessageSerializer<Object>(getClass().getClassLoader())).connect(address);
            } catch (ConnectException e) {
                //this means the daemon died without removing its address from the registry
                //we can safely remove this address now
                LOGGER.warn("We cannot connect to the daemon at " + address + " due to " + e + ". "
                        + "We will not remove this daemon from the registry because the connection issue may have been temporary.");
                //TODO SF it might be good to store in the registry the number of failed attempts to connect to the deamon
                //if the number is high we may decide to remove the daemon from the registry
                //daemonRegistry.remove(address);
            }
        }
        return null;
    }

    public Connection<Object> connect() {
        Connection<Object> connection = findConnection(daemonRegistry.getIdle());
        if (connection != null) {
            return connection;
        }

        LOGGER.info("Starting Gradle daemon");
        startDaemon();
        Date expiry = new Date(System.currentTimeMillis() + 30000L);
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
        } while (System.currentTimeMillis() < expiry.getTime());

        throw new GradleException("Timeout waiting to connect to Gradle daemon.");
    }

    public T getDaemonRegistry() {
        return daemonRegistry;
    }

    abstract protected void startDaemon();
}
