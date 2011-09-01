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

import org.gradle.messaging.remote.internal.Connection;

/**
 * A daemon connector establishes a connection to either an already running daemon, or a newly started daemon.
 */
public interface DaemonConnector {

    /**
     * Attempts to connect to the daemon, if it is running.
     *
     * @return The connection, or null if not running.
     */
    public Connection<Object> maybeConnect();

    /**
     * Connects to the daemon, starting it if required.
     *
     * @return The connection. Never returns null.
     */
    public Connection<Object> connect();

    /**
     * The registry that this connector is using.
     *
     * @return The registry. Never returns null.
     */
    public DaemonRegistry getDaemonRegistry();

}
