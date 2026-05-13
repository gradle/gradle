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
package org.gradle.tooling.daemon;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Entry point for inspecting and stopping Gradle daemons under a Gradle user home directory.
 *
 * <p>Obtained via {@link org.gradle.tooling.GradleConnector#newDaemonManagement()}. Unlike
 * {@link org.gradle.tooling.ProjectConnection}, a {@code DaemonManagement} instance does
 * not require a project directory: it operates against the user's per-machine daemon
 * registries, spanning every Gradle version that has run on the machine.
 *
 * <p>Listings cover daemons registered by Gradle 5.0 and later. Stopping operates against
 * any version whose daemon protocol implements the stable {@code Stop} wire message
 * (Gradle 2.2 and later).
 *
 * @since 9.6
 */
@Incubating
public interface DaemonManagement {

    /**
     * Returns every daemon known to the registries under the configured Gradle user home,
     * across all installed Gradle versions.
     */
    List<GradleDaemon> listDaemons();

    /**
     * Returns daemons of a specific Gradle version.
     */
    List<GradleDaemon> listDaemons(String gradleVersion);

    /**
     * Stops every daemon listed by {@link #listDaemons()}. Best effort.
     */
    void stopAll();

    /**
     * Stops the given daemon.
     */
    StopResult stop(GradleDaemon daemon);

    /**
     * Stops a daemon by process id. Returns {@link StopResult#NOT_FOUND} if no listed
     * daemon has the given PID.
     */
    StopResult stopByPid(long pid);

    /**
     * Stops a daemon by UID. Returns {@link StopResult#NOT_FOUND} if no listed daemon
     * has the given UID.
     */
    StopResult stopByUid(String uid);
}
