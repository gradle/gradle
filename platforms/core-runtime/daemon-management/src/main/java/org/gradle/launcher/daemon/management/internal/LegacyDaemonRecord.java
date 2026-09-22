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

package org.gradle.launcher.daemon.management.internal;

import org.gradle.internal.remote.Address;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

/**
 * One daemon as described by the per-version registry of the Gradle version that wrote it.
 */
public class LegacyDaemonRecord {

    private final GradleVersion gradleVersion;
    private final @Nullable String uid;
    private final Address address;
    private final byte[] token;
    private final String state;
    private final @Nullable Long pid;
    private final @Nullable String javaHome;

    public LegacyDaemonRecord(GradleVersion gradleVersion, @Nullable String uid, Address address, byte[] token, String state, @Nullable Long pid, @Nullable String javaHome) {
        this.gradleVersion = gradleVersion;
        this.uid = uid;
        this.address = address;
        this.token = token.clone();
        this.state = state;
        this.pid = pid;
        this.javaHome = javaHome;
    }

    public GradleVersion getGradleVersion() {
        return gradleVersion;
    }

    /**
     * The daemon's own identifier, absent when the registry record could not be read that far.
     */
    public @Nullable String getUid() {
        return uid;
    }

    public Address getAddress() {
        return address;
    }

    public byte[] getToken() {
        return token.clone();
    }

    /**
     * The state the daemon last recorded for itself. This is what the registry says, which can lag what
     * the daemon is actually doing, so it is only used where no live answer is available.
     */
    public String getState() {
        return state;
    }

    public @Nullable Long getPid() {
        return pid;
    }

    public @Nullable String getJavaHome() {
        return javaHome;
    }

    @Override
    public String toString() {
        return "LegacyDaemonRecord{gradleVersion=" + gradleVersion.getVersion() + ", pid=" + pid + ", state=" + state + "}";
    }
}
