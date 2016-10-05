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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * A {@link LoggingSourceSystem} which configures JUL to route logging events to SLF4J.
 */
public class JavaUtilLoggingSystem implements LoggingSourceSystem {

    private static final Map<LogLevel, Level> LOG_LEVEL_MAPPING = new HashMap<LogLevel, Level>();

    // Gradle's log levels correspond to slf4j log levels
    // as implemented in OutputEventListenerBackedLogger.
    // These levels are mapped to java.util.logging.Levels
    // corresponding to the mapping implemented in the
    // SLF4JBridgeHandler which is installed by this logging system.
    static {
        LOG_LEVEL_MAPPING.put(LogLevel.DEBUG, Level.FINE);
        LOG_LEVEL_MAPPING.put(LogLevel.INFO, Level.INFO);
        LOG_LEVEL_MAPPING.put(LogLevel.LIFECYCLE, Level.WARNING);
        LOG_LEVEL_MAPPING.put(LogLevel.WARN, Level.WARNING);
        LOG_LEVEL_MAPPING.put(LogLevel.QUIET, Level.SEVERE);
        LOG_LEVEL_MAPPING.put(LogLevel.ERROR, Level.SEVERE);
    }

    private final Logger logger;
    private boolean installed;

    public JavaUtilLoggingSystem() {
        logger = Logger.getLogger("");
    }

    @Override
    public Snapshot on(LogLevel minimumLevel, LogLevel defaultLevel) {
        SnapshotImpl snapshot = new SnapshotImpl(installed, logger.getLevel());
        install(LOG_LEVEL_MAPPING.get(minimumLevel));
        return snapshot;
    }

    @Override
    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        if (snapshot.installed) {
            install(snapshot.level);
        } else {
            uninstall(snapshot.level);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new SnapshotImpl(installed, logger.getLevel());
    }

    private void uninstall(Level level) {
        if (!installed) {
            return;
        }

        LogManager.getLogManager().reset();
        logger.setLevel(level);
        installed = false;
    }

    private void install(Level level) {
        if (installed) {
            return;
        }

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        logger.setLevel(level);
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
