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
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot of a single Gradle daemon as known to the user's daemon registry.
 *
 * @since 9.6
 */
@Incubating
public interface GradleDaemon {

    /**
     * Unique identifier of the daemon, assigned at process start.
     */
    String getUid();

    /**
     * Operating system process id.
     */
    long getPid();

    /**
     * The daemon's current state.
     */
    DaemonStatus getStatus();

    /**
     * Version of Gradle the daemon is running.
     */
    String getGradleVersion();

    /**
     * Java home the daemon was launched against.
     */
    File getJavaHome();

    /**
     * Major version of the daemon's JVM, or {@code null} for daemons of Gradle versions
     * older than 8.8 (the version where this metadata was first stored in the registry).
     */
    @Nullable
    Integer getJavaMajorVersion();

    /**
     * Vendor of the daemon's JVM, or {@code null} for daemons of Gradle versions
     * older than 8.10 (the version where this metadata was first stored in the registry).
     */
    @Nullable
    String getJavaVendor();

    /**
     * How long the daemon will wait while idle before shutting itself down.
     */
    Duration getIdleTimeout();

    /**
     * The last time the daemon transitioned from idle to busy.
     */
    Instant getLastBusy();

    /**
     * JVM arguments the daemon was launched with.
     */
    List<String> getJvmArguments();
}
