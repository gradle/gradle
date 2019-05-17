/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.logging.config;

import org.gradle.api.logging.LogLevel;

/**
 * Adapts a {@link LoggingConfigurer} to a {@link LoggingSourceSystem}.
 */
public class LoggingSystemAdapter implements LoggingSourceSystem {
    private final LoggingConfigurer configurer;
    private boolean enabled;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    public LoggingSystemAdapter(LoggingConfigurer configurer) {
        this.configurer = configurer;
    }

    @Override
    public Snapshot snapshot() {
        return new SnapshotImpl(enabled, logLevel);
    }

    @Override
    public Snapshot setLevel(LogLevel logLevel) {
        Snapshot snapshot = snapshot();
        if (this.logLevel != logLevel) {
            this.logLevel = logLevel;
            if (enabled) {
                configurer.configure(logLevel);
            }
        }
        return snapshot;
    }

    @Override
    public Snapshot startCapture() {
        Snapshot snapshot = snapshot();
        if (!enabled) {
            enabled = true;
            configurer.configure(logLevel);
        }
        return snapshot;
    }

    @Override
    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        logLevel = snapshot.level;
        enabled = snapshot.enabled;
        configurer.configure(logLevel);
    }

    private class SnapshotImpl implements Snapshot {
        private final boolean enabled;
        private final LogLevel level;

        SnapshotImpl(boolean enabled, LogLevel level) {
            this.enabled = enabled;
            this.level = level;
        }
    }
}
