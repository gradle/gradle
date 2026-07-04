/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.management;

import org.gradle.launcher.daemon.context.DaemonConnectDetails;

import java.util.Collection;
import java.util.List;

/**
 * The entry point for accessing and managing the Gradle daemons of the current version.
 *
 * <p>This is the single abstraction that the {@code gradle --status} / {@code --stop} commands, the standalone
 * daemon management tool, and the Tooling API build on. It consolidates registry discovery and protocol-based
 * control (status, stop, stop-when-idle) so callers do not each re-implement "read the registry, connect,
 * dispatch". It is deliberately scoped to the current Gradle version; cross-version management can be layered
 * on later.
 *
 * <p>Obtain an instance with {@link DaemonManagement}, which hides all of the service wiring.
 */
public interface ManagedDaemons {

    /**
     * Footer printed after the {@code --status} listing, noting that only current-version daemons are shown.
     */
    String STATUS_FOOTER = "Only Daemons for the current Gradle version are displayed.";

    /**
     * The daemons currently recorded in the registry, as individually controllable handles.
     */
    List<ManagedDaemon> getDaemons();

    /**
     * Stops all daemons immediately, blocking until they have stopped (or a timeout elapses).
     */
    void stopAll();

    /**
     * Requests that all daemons stop once idle. Returns without waiting for them to stop.
     */
    void stopAllWhenIdle();

    /**
     * Requests that the given daemons stop once idle. Returns without waiting for them to stop. Used to
     * gracefully shut down a specific set of daemons (for example the ones a Tooling API provider started).
     *
     * <p>{@link DaemonConnectDetails} is still exposed here as the daemon reference type; replacing it with an
     * API-owned reference is a follow-up once the connection details are relocated behind this layer.
     */
    void stopWhenIdle(Collection<? extends DaemonConnectDetails> daemons);

    /**
     * Prints the status of running and recently stopped daemons (the {@code gradle --status} listing).
     */
    void reportStatus();
}
