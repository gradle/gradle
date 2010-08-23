/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;

/**
 * Adapts a {@link LoggingConfigurer} to a {@link LoggingSystem}.
 */
public class LoggingSystemAdapter implements LoggingSystem {
    private final LoggingConfigurer configurer;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    public LoggingSystemAdapter(LoggingConfigurer configurer) {
        this.configurer = configurer;
    }

    public Snapshot snapshot() {
        return new SnapshotImpl(logLevel);
    }

    public Snapshot off() {
        return new SnapshotImpl(logLevel);
    }

    public Snapshot on(LogLevel level) {
        SnapshotImpl snapshot = new SnapshotImpl(logLevel);
        setLevel(level);
        return snapshot;
    }

    public void restore(Snapshot state) {
        LogLevel oldLevel = ((SnapshotImpl) state).level;
        this.logLevel = oldLevel;
        if (oldLevel != null) {
            configurer.configure(oldLevel);
        }
    }

    private void setLevel(LogLevel level) {
        configurer.configure(level);
        this.logLevel = level;
    }

    private class SnapshotImpl implements Snapshot {
        private final LogLevel level;

        public SnapshotImpl(LogLevel level) {
            this.level = level;
        }
    }
}
