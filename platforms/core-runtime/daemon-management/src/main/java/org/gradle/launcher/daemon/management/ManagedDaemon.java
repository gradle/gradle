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

import org.jspecify.annotations.Nullable;

/**
 * A handle to one daemon, whatever Gradle version it runs.
 *
 * <p>How the daemon is reached is the handle's business, not the caller's. A caller asks for a daemon to
 * stop; whether that means the index, the older registry format, or a signal to the process is decided
 * here, from what the daemon's version supports.
 */
public interface ManagedDaemon {

    /**
     * How this daemon was found.
     */
    enum Discovery {
        /**
         * From the cross-version index the daemon published about itself.
         */
        INDEX,
        /**
         * From the per-version registry, read with the layout that version wrote. This is how daemons of
         * versions released before the index existed are found.
         */
        LEGACY_REGISTRY
    }

    /**
     * What happened when a build was asked to cancel.
     */
    enum CancelOutcome {
        /**
         * The daemon was running a build and has been asked to cancel it.
         */
        REQUESTED,
        /**
         * The daemon was not running a build.
         */
        NOTHING_RUNNING,
        /**
         * This daemon's Gradle version has no way to cancel a build from outside the process that
         * started it.
         */
        UNSUPPORTED,
        /**
         * The daemon could not be reached.
         */
        FAILED
    }

    String getGradleVersion();

    String getUid();

    @Nullable Long getPid();

    Discovery getDiscovery();

    /**
     * Whether the daemon still answers. A daemon that does not is either shutting down or gone, and its
     * entry is stale.
     */
    boolean isAlive();

    DaemonStatus status();

    /**
     * Stops the daemon as soon as it can, abandoning any build it is running.
     *
     * @return whether the daemon is gone
     */
    boolean stop();

    /**
     * Asks the daemon to stop once it finishes what it is doing. A daemon running a build finishes that
     * build first.
     *
     * @return whether the request was delivered
     */
    boolean stopWhenIdle();

    /**
     * Cancels the build the daemon is running, leaving the daemon itself running.
     */
    CancelOutcome cancelBuild();
}
