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

package org.gradle.cache.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.internal.file.Deleter;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Cleans up the build output files written in agent mode, which live in one directory per invocation in the project cache directory.
 * Removes the directories of invocations whose output hasn't been modified for a specified retention period.
 */
@NullMarked
public class AgentOutputCleanupAction implements MonitoredCleanupAction {
    /**
     * Relative to the project cache directory.
     */
    public static final String BUILDS_DIR_PATH = "agent/builds";

    // Kept outside of the builds directory, so that it contains nothing but invocation directories
    @VisibleForTesting
    static final String GC_FILE_PATH = "agent/gc.properties";

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentOutputCleanupAction.class);

    private final File projectCacheDir;
    private final Supplier<Long> removeUnusedEntriesOlderThan;
    private final Deleter deleter;
    private final CleanupFrequency cleanupFrequency;

    public AgentOutputCleanupAction(File projectCacheDir, Supplier<Long> removeUnusedEntriesOlderThan, Deleter deleter, CleanupFrequency cleanupFrequency) {
        this.projectCacheDir = projectCacheDir;
        this.removeUnusedEntriesOlderThan = removeUnusedEntriesOlderThan;
        this.deleter = deleter;
        this.cleanupFrequency = cleanupFrequency;
    }

    /**
     * Whether agent mode has ever written output to the project cache directory.
     */
    public boolean hasOutput() {
        return getBuildsDir().isDirectory();
    }

    @Override
    public String getDisplayName() {
        return "Deleting old agent mode build output in " + getBuildsDir();
    }

    @Override
    public boolean execute(CleanupProgressMonitor progressMonitor) {
        // Check if there is output, so that the marker file is only ever created next to existing output
        if (!hasOutput() || !requiresCleanup()) {
            return false;
        }
        performCleanup(progressMonitor);
        GFileUtils.touch(getGcFile());
        return true;
    }

    private boolean requiresCleanup() {
        File gcFile = getGcFile();
        Instant lastCleanupTime = gcFile.exists() ? Instant.ofEpochMilli(gcFile.lastModified()) : null;
        return cleanupFrequency.requiresCleanup(lastCleanupTime);
    }

    private void performCleanup(CleanupProgressMonitor progressMonitor) {
        File[] invocationDirs = getBuildsDir().listFiles(File::isDirectory);
        if (invocationDirs == null) {
            return;
        }
        long maxAge = removeUnusedEntriesOlderThan.get();
        for (File invocationDir : invocationDirs) {
            if (lastModified(invocationDir) < maxAge) {
                try {
                    LOGGER.debug("Deleting old agent mode build output: {}", invocationDir);
                    deleter.deleteRecursively(invocationDir);
                    progressMonitor.incrementDeleted();
                } catch (IOException e) {
                    LOGGER.warn("Could not delete old agent mode build output: {}", invocationDir.getAbsolutePath(), e);
                    progressMonitor.incrementSkipped();
                }
            } else {
                progressMonitor.incrementSkipped();
            }
        }
    }

    private static long lastModified(File invocationDir) {
        long lastModified = invocationDir.lastModified();
        File[] files = invocationDir.listFiles();
        if (files != null) {
            for (File file : files) {
                lastModified = Math.max(lastModified, file.lastModified());
            }
        }
        return lastModified;
    }

    private File getBuildsDir() {
        return new File(projectCacheDir, BUILDS_DIR_PATH);
    }

    private File getGcFile() {
        return new File(projectCacheDir, GC_FILE_PATH);
    }
}
