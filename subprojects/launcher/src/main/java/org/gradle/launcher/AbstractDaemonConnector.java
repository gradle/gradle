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
package org.gradle.launcher;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.UncheckedException;

import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector;

import java.util.List;
import java.util.Date;

abstract public class AbstractDaemonConnector implements DaemonConnector {

    private static final Logger LOGGER = Logging.getLogger(AbstractDaemonConnector.class);

    private final DaemonRegistry daemonRegistry;

    protected AbstractDaemonConnector(DaemonRegistry daemonRegistry) {
        this.daemonRegistry = daemonRegistry;
    }

    public Connection<Object> maybeConnect() {
        return findConnection(daemonRegistry.getAll());
    }

    private Connection<Object> findConnection(List<DaemonStatus> statuses) {
        for (DaemonStatus status : statuses) {
            try {
                return new TcpOutgoingConnector<Object>(new DefaultMessageSerializer<Object>(getClass().getClassLoader())).connect(status.getAddress());
            } catch (ConnectException e) {
                // Ignore
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

    public DaemonRegistry getDaemonRegistry() {
        return daemonRegistry;
    }

    abstract protected void startDaemon();
}
