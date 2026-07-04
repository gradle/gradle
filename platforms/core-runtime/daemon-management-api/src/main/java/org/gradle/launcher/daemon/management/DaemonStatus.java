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

import java.util.Objects;

/**
 * The live status of a single daemon, as reported over the protocol. This is an API-owned value type so the
 * wire-protocol status message does not leak through {@link ManagedDaemon#getStatus()}.
 */
public final class DaemonStatus {

    @Nullable
    private final Long pid;
    private final String version;
    private final String state;

    public DaemonStatus(@Nullable Long pid, String version, String state) {
        this.pid = pid;
        this.version = version;
        this.state = state;
    }

    /**
     * The process id of the daemon, or {@code null} if it is not known.
     */
    @Nullable
    public Long getPid() {
        return pid;
    }

    /**
     * The Gradle version the daemon is running.
     */
    public String getVersion() {
        return version;
    }

    /**
     * The daemon state (for example {@code IDLE} or {@code BUSY}).
     */
    public String getState() {
        return state;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DaemonStatus that = (DaemonStatus) o;
        return Objects.equals(pid, that.pid) && version.equals(that.version) && state.equals(that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, version, state);
    }

    @Override
    public String toString() {
        return "DaemonStatus{pid=" + pid + ", version='" + version + "', state='" + state + "'}";
    }
}
