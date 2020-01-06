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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.internal.remote.internal.MessageIOException;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.protocol.Message;

import javax.annotation.Nullable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A simple wrapper for the connection to a daemon.
 *
 * <p>Currently, dispatch is thread safe, and receive is not.
 */
public class DaemonClientConnection implements Connection<Message> {
    private final static Logger LOG = Logging.getLogger(DaemonClientConnection.class);
    private final RemoteConnection<Message> connection;
    private final DaemonConnectDetails daemon;
    private final StaleAddressDetector staleAddressDetector;
    private boolean hasReceived;
    private final Lock dispatchLock = new ReentrantLock();

    public DaemonClientConnection(RemoteConnection<Message> connection, DaemonConnectDetails daemon, StaleAddressDetector staleAddressDetector) {
        this.connection = connection;
        this.daemon = daemon;
        this.staleAddressDetector = staleAddressDetector;
    }

    public DaemonConnectDetails getDaemon() {
        return daemon;
    }

    @Override
    public void dispatch(Message message) throws DaemonConnectionException {
        LOG.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
        try {
            dispatchLock.lock();
            try {
                connection.dispatch(message);
                connection.flush();
            } finally {
                dispatchLock.unlock();
            }
        } catch (MessageIOException e) {
            LOG.debug("Problem dispatching message to the daemon. Performing 'on failure' operation...");
            if (!hasReceived && staleAddressDetector.maybeStaleAddress(e)) {
                throw new StaleDaemonAddressException("Could not dispatch a message to the daemon.", e);
            }
            throw new DaemonConnectionException("Could not dispatch a message to the daemon.", e);
        }
    }

    @Override
    @Nullable
    public Message receive() throws DaemonConnectionException {
        try {
            return connection.receive();
        } catch (MessageIOException e) {
            LOG.debug("Problem receiving message to the daemon. Performing 'on failure' operation...");
            if (!hasReceived && staleAddressDetector.maybeStaleAddress(e)) {
                throw new StaleDaemonAddressException("Could not receive a message from the daemon.", e);
            }
            throw new DaemonConnectionException("Could not receive a message from the daemon.", e);
        } finally {
            hasReceived = true;
        }
    }

    @Override
    public void stop() {
        LOG.debug("thread {}: connection stop", Thread.currentThread().getId());
        connection.stop();
    }

    interface StaleAddressDetector {
        /**
         * @return true if the failure should be considered due to a stale address.
         */
        boolean maybeStaleAddress(Exception failure);
    }
}
