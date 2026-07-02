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

import org.gradle.launcher.daemon.protocol.Status;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A handle to a single running Gradle daemon of the current version, obtained from {@link ManagedDaemons}.
 *
 * <p>All operations are best-effort and go over the daemon protocol on the loopback connection: they never
 * throw because the daemon had already gone away by the time it was contacted.
 */
@NullMarked
public interface ManagedDaemon {

    /**
     * The process id of the daemon as recorded in the registry, or {@code null} if it is not known.
     */
    @Nullable
    Long getPid();

    /**
     * Queries the daemon for its live status (running/idle/busy and version) over the protocol.
     *
     * @return the status, or {@code null} if the daemon could not be reached.
     */
    @Nullable
    Status getStatus();

    /**
     * Requests that the daemon stop immediately.
     */
    void stop();

    /**
     * Requests that the daemon stop once it becomes idle, letting any in-progress build finish first.
     */
    void stopWhenIdle();
}
