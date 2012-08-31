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
import org.gradle.api.Nullable;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.messaging.remote.internal.Connection;

/**
 * A simple wrapper for the connection to a daemon plus its password.
 */
public class DaemonClientConnection implements Connection<Object> {

    final Connection<Object> connection;
    private final String uid;
    private DaemonDiagnostics diagnostics;
    final Runnable onFailure;
    private final static Logger LOG = Logging.getLogger(DaemonClientConnection.class);

    public DaemonClientConnection(Connection<Object> connection, String uid, DaemonDiagnostics diagnostics, Runnable onFailure) {
        this.connection = connection;
        this.uid = uid;
        this.diagnostics = diagnostics;
        this.onFailure = onFailure;
    }

    /**
     * @return diagnostics. Can be null - it means we don't have process diagnostics.
     */
    @Nullable
    public DaemonDiagnostics getDaemonDiagnostics() {
        return diagnostics;
    }

    public void requestStop() {
        connection.requestStop();
    }

    public String getUid() {
        return uid;
    }

    public void dispatch(Object message) {
        try {
            connection.dispatch(message);
        } catch (Exception e) {
            LOG.debug("Problem dispatching message to the daemon. Performing 'on failure' operation...");
            onFailure.run();
            throw new GradleException("Unable to dispatch the message to the daemon.", e);
        }
    }

    public Object receive() {
        try {
            return connection.receive();
        } catch (Exception e) {
            LOG.debug("Problem receiving message to the daemon. Performing 'on failure' operation...");
            onFailure.run();
            throw new GradleException("Unable to receive a message from the daemon.", e);
        }
    }

    public void stop() {
        connection.stop();
    }
}