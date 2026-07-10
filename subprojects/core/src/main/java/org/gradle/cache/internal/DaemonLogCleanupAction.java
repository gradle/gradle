/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.internal.DefaultGradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import static org.gradle.launcher.daemon.logging.DaemonLogConstants.DAEMON_LOG_PREFIX;
import static org.gradle.launcher.daemon.logging.DaemonLogConstants.DAEMON_LOG_SUFFIX;

/**
 * Cleans up old daemon log files from the daemon base directory.
 * Removes log files that haven't been modified for a specified retention period.
 */
public class DaemonLogCleanupAction implements MonitoredCleanupAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonLogCleanupAction.class);

    static final long DEFAULT_RETENTION_DAYS = 14;

    private final File daemonBaseDir;
    private final Deleter deleter;
    private final Supplier<Long> timeInMillis;

    /**
     * Creates a new daemon log cleanup action.
     *
     * @param daemonBaseDir the daemon base directory containing versioned daemon log directories
     * @param deleter the deleter to use for removing files
     */
    public DaemonLogCleanupAction(File daemonBaseDir, Deleter deleter, Supplier<Long> timeInMillis) {
        this.daemonBaseDir = daemonBaseDir;
        this.deleter = deleter;
        this.timeInMillis = timeInMillis;
    }

    @Override
    public String getDisplayName() {
        return "Deleting old daemon log files in " + daemonBaseDir;
    }

    @Override
    public boolean execute(CleanupProgressMonitor progressMonitor) {
        Timer timer = Time.startTimer();
        performCleanup(progressMonitor);
        LOGGER.debug("Processed daemon logs at {} for cleanup in {}", daemonBaseDir, timer.getElapsed());
        return true;
    }

    private void performCleanup(CleanupProgressMonitor progressMonitor) {
        long maxAge = timeInMillis.get();

        File[] daemonLogDirectories = daemonBaseDir.listFiles(file ->
            file.isDirectory() && DefaultGradleVersion.VERSION_PATTERN.matcher(file.getName()).matches());

        if (daemonLogDirectories == null) {
            LOGGER.debug("Daemon log root directory not found: {}", daemonBaseDir.getAbsolutePath());
            return;
        }

        for (File daemonLogDirectory : daemonLogDirectories) {
            cleanupLogsInDirectory(daemonLogDirectory, maxAge, progressMonitor);
        }
    }

    private void cleanupLogsInDirectory(File daemonLogDirectory, long maxAge, CleanupProgressMonitor progressMonitor) {
        File[] logFiles = daemonLogDirectory.listFiles(f ->
            f.isFile() && f.getName().endsWith(DAEMON_LOG_SUFFIX) && f.getName().startsWith(DAEMON_LOG_PREFIX));

        if (logFiles == null) {
            LOGGER.debug("Version specific daemon log directory not found: {}", daemonLogDirectory.getAbsolutePath());
            return;
        }

        for (File logFile : logFiles) {
            if (logFile.lastModified() < maxAge) {
                try {
                    LOGGER.debug("Deleting old daemon log file: {}", logFile);
                    deleter.delete(logFile);
                    progressMonitor.incrementDeleted();
                } catch (IOException e) {
                    LOGGER.warn("Could not delete old daemon log file: {}", logFile.getAbsolutePath(), e);
                    progressMonitor.incrementSkipped();
                }
            } else {
                progressMonitor.incrementSkipped();
            }
        }
    }
}

