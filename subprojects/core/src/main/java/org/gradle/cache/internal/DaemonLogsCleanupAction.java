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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cleanup action that removes daemon log files older than a configurable retention period.
 * This uses the standard Gradle cleanup infrastructure to provide progress reporting,
 * build operations, and configurability.
 */
public class DaemonLogsCleanupAction implements MonitoredCleanupAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonLogsCleanupAction.class);
    private static final String DAEMON_LOG_PREFIX = "daemon-";
    private static final String DAEMON_LOG_SUFFIX = ".out.log";

    private final File daemonBaseDir;
    private final Supplier<Long> retentionTimestampSupplier;
    private final Deleter deleter;

    public DaemonLogsCleanupAction(File daemonBaseDir, Supplier<Long> retentionTimestampSupplier, Deleter deleter) {
        this.daemonBaseDir = daemonBaseDir;
        this.retentionTimestampSupplier = retentionTimestampSupplier;
        this.deleter = deleter;
    }

    @Override
    public String getDisplayName() {
        return "Deleting unused daemon logs in " + daemonBaseDir;
    }

    @Override
    public boolean execute(@NonNull CleanupProgressMonitor progressMonitor) {
        Timer timer = Time.startTimer();
        boolean cleaned = performCleanup(progressMonitor);
        LOGGER.debug("Processed daemon logs at {} for cleanup in {}", daemonBaseDir, timer.getElapsed());
        return cleaned;
    }

    private boolean performCleanup(CleanupProgressMonitor progressMonitor) {
        if (!daemonBaseDir.exists() || !daemonBaseDir.isDirectory()) {
            LOGGER.debug("Daemon base directory does not exist: {}", daemonBaseDir);
            return false;
        }

        File[] daemonLogDirectories = daemonBaseDir.listFiles(file ->
            file.isDirectory() && DefaultGradleVersion.VERSION_PATTERN.matcher(file.getName()).matches()
        );

        if (daemonLogDirectories == null) {
            LOGGER.warn("Could not list daemon log directories for cleanup in: {}", daemonBaseDir.getAbsolutePath());
            return false;
        }

        boolean anyCleaned = false;
        long maxAge = retentionTimestampSupplier.get();

        for (File daemonLogDirectory : daemonLogDirectories) {
            File[] logFiles = daemonLogDirectory.listFiles(f ->
                f.isFile() &&
                f.getName().endsWith(DAEMON_LOG_SUFFIX) &&
                f.getName().startsWith(DAEMON_LOG_PREFIX)
            );

            if (logFiles == null) {
                LOGGER.warn("Could not list log files for cleanup in: {}", daemonLogDirectory.getAbsolutePath());
                continue;
            }

            for (File logFile : logFiles) {
                if (logFile.lastModified() < maxAge) {
                    try {
                        deleter.delete(logFile);
                        progressMonitor.incrementDeleted();
                        anyCleaned = true;
                        LOGGER.debug("Deleted old daemon log file: {}", logFile);
                    } catch (IOException e) {
                        LOGGER.warn("Could not delete old log file: {}", logFile.getAbsolutePath(), e);
                        progressMonitor.incrementSkipped();
                    }
                } else {
                    progressMonitor.incrementSkipped();
                }
            }
        }

        return anyCleaned;
    }
}
