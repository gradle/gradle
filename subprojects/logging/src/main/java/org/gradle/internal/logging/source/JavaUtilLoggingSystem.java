/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.source;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * A {@link LoggingSourceSystem} which configures JUL to route logging events to SLF4J.
 */
public class JavaUtilLoggingSystem implements LoggingSourceSystem {
    private final Logger logger;
    private boolean installed;

    public JavaUtilLoggingSystem() {
        logger = Logger.getLogger("");
    }

    @Override
    public Snapshot on(LogLevel minimumLevel, LogLevel defaultLevel) {
        SnapshotImpl snapshot = new SnapshotImpl(installed, logger.getLevel());
        install();
        return snapshot;
    }

    @Override
    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        if (snapshot.installed) {
            install();
        } else {
            uninstall(snapshot.level);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new SnapshotImpl(installed, logger.getLevel());
    }

    private void uninstall(java.util.logging.Level level) {
        if (!installed) {
            return;
        }

        LogManager.getLogManager().reset();
        logger.setLevel(level);
        installed = false;
    }

    private void install() {
        if (installed) {
            return;
        }

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        logger.setLevel(java.util.logging.Level.FINE);
        installed = true;
    }

    private static class SnapshotImpl implements Snapshot {
        private final boolean installed;
        private final java.util.logging.Level level;

        public SnapshotImpl(boolean installed, java.util.logging.Level level) {
            this.installed = installed;
            this.level = level;
        }
    }
}
