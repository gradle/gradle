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
package org.gradle.launcher.daemon.client;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The entry point for accessing and managing the Gradle daemons of the current version.
 *
 * <p>This is the single abstraction that the {@code gradle --status} / {@code --stop} commands and the
 * standalone daemon management tool build on. It consolidates registry discovery and protocol-based control
 * (status, stop, stop-when-idle) so callers do not each re-implement "read the registry, connect, dispatch".
 * It is deliberately scoped to the current Gradle version; cross-version management can be layered on later.
 */
@NullMarked
public interface ManagedDaemons {

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
     * Prints the status of running and recently stopped daemons (the {@code gradle --status} listing).
     */
    void reportStatus();
}
