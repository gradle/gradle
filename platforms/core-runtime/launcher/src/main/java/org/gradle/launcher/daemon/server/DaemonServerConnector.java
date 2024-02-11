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

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.remote.Address;

/**
 * Opens a server connection for clients to connect to communicate with a daemon.
 * <p>
 * A server connector should only be used by one daemon, and has a single use lifecycle.
 * Implementations must be threadsafe so that start/stop can be called from different threads.
 */
public interface DaemonServerConnector extends Stoppable {

    /**
     * Starts accepting connections, passing any established connections to the given handler.
     * <p>
     * When this method returns, the daemon will be ready to accept connections.
     *
     * @return the address that clients can use to connect
     * @throws IllegalStateException if this method has previously been called on this object, or if the stop method has previously been called on this object.
     */
    Address start(IncomingConnectionHandler handler, Runnable connectionErrorHandler);

    /**
     * Stops accepting new connections, and blocks until all active connections close.
     */
    @Override
    public void stop();

}
