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

import org.gradle.api.Nullable;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;

/**
 * A daemon connector establishes a connection to a daemon.
 */
public interface DaemonConnector {

    /**
     * Attempts to connect to a daemon at the given address.
     *
     * @return A connection to a matching daemon, or null if not running.
     */
    @Nullable
    DaemonClientConnection maybeConnect(DaemonConnectDetails daemonAddress);

    /**
     * Attempts to connect to a daemon that matches the given constraint.
     *
     * @return A connection to a matching daemon, or null if none running.
     */
    @Nullable
    DaemonClientConnection maybeConnect(ExplainingSpec<DaemonContext> constraint);

    /**
     * Connects to a daemon that matches the given constraint, starting one if required.
     *
     * @return A connection to a matching daemon. Never returns null.
     */
    DaemonClientConnection connect(ExplainingSpec<DaemonContext> constraint);

    /**
     * Starts a new daemon and returns a connection to it.
     */
    DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint);

}
