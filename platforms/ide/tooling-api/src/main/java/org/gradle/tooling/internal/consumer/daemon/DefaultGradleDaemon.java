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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.tooling.daemon.DaemonStatus;
import org.gradle.tooling.daemon.GradleDaemon;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

final class DefaultGradleDaemon implements GradleDaemon {

    private final DaemonInfoView info;
    private final String gradleVersion;

    DefaultGradleDaemon(DaemonInfoView info, String gradleVersion) {
        this.info = info;
        this.gradleVersion = gradleVersion;
    }

    @Override
    public String getUid() {
        return info.context.uid;
    }

    @Override
    public long getPid() {
        return info.context.pid == null ? -1L : info.context.pid;
    }

    @Override
    public DaemonStatus getStatus() {
        switch (info.state) {
            case IDLE:           return DaemonStatus.IDLE;
            case BUSY:           return DaemonStatus.BUSY;
            case CANCELED:       return DaemonStatus.CANCELED;
            case STOPPED:        return DaemonStatus.STOPPED;
            case STOP_REQUESTED: return DaemonStatus.BUSY;
            case UNKNOWN:
            default:             return DaemonStatus.UNKNOWN;
        }
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion;
    }

    @Override
    public File getJavaHome() {
        return info.context.javaHome;
    }

    @Override
    @Nullable
    public Integer getJavaMajorVersion() {
        return info.context.javaMajorVersion;
    }

    @Override
    @Nullable
    public String getJavaVendor() {
        return info.context.javaVendor;
    }

    @Override
    public Duration getIdleTimeout() {
        Integer millis = info.context.idleTimeoutMillis;
        return millis == null ? Duration.ZERO : Duration.ofMillis(millis);
    }

    @Override
    public Instant getLastBusy() {
        return Instant.ofEpochMilli(info.lastBusyMillis);
    }

    @Override
    public List<String> getJvmArguments() {
        return Collections.unmodifiableList(info.context.daemonOpts);
    }
}
