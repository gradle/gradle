/*
 * Copyright 2018 the original author or authors.
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
import com.google.common.base.Preconditions;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.specs.Spec;
import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.SortedSet;
import java.util.function.Supplier;

public class VersionSpecificCacheCleanupAction implements MonitoredCleanupAction {
    private final static String FILE_HASHES_CACHE_KEY =  CrossBuildFileHashCache.Kind.FILE_HASHES.getCacheId();

    @VisibleForTesting static final String MARKER_FILE_PATH = FILE_HASHES_CACHE_KEY + "/" + FILE_HASHES_CACHE_KEY + ".lock";
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionSpecificCacheCleanupAction.class);

    private final VersionSpecificCacheDirectoryScanner versionSpecificCacheDirectoryScanner;
    private final Supplier<Long> releaseTimestampSupplier;
    private final Supplier<Long> snapshotTimestampSupplier;
    private final Deleter deleter;
    private final CleanupFrequency cleanupFrequency;

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, Supplier<Long> releasesAndSnapshotTimestampSupplier, Deleter deleter, CleanupFrequency cleanupFrequency) {
        this(cacheBaseDir, releasesAndSnapshotTimestampSupplier, releasesAndSnapshotTimestampSupplier, deleter, cleanupFrequency);
    }

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, Supplier<Long> releaseTimestampSupplier, Supplier<Long> snapshotTimestampSupplier, Deleter deleter, CleanupFrequency cleanupFrequency) {
        this.deleter = deleter;
        Preconditions.checkArgument(releaseTimestampSupplier.get() <= snapshotTimestampSupplier.get(),
            "release timestamp (%s) must supply a timestamp older than or equal to snapshot timestamp (%s)", releaseTimestampSupplier.get(), snapshotTimestampSupplier.get());
        this.versionSpecificCacheDirectoryScanner = new VersionSpecificCacheDirectoryScanner(cacheBaseDir);
        this.releaseTimestampSupplier = releaseTimestampSupplier;
        this.snapshotTimestampSupplier = snapshotTimestampSupplier;
        this.cleanupFrequency = cleanupFrequency;
    }

    @Override
    @Nonnull
    public String getDisplayName() {
        return "Deleting unused version-specific caches in " + versionSpecificCacheDirectoryScanner.getBaseDir();
    }

    @Override
    public boolean execute(@Nonnull CleanupProgressMonitor progressMonitor) {
        if (requiresCleanup()) {
            Timer timer = Time.startTimer();
            performCleanup(progressMonitor);
            LOGGER.debug("Processed version-specific caches at {} for cleanup in {}", versionSpecificCacheDirectoryScanner.getBaseDir(), timer.getElapsed());
            return true;
        }
        return false;
    }

    private boolean requiresCleanup() {
        Instant lastCleanupTime;

        File gcFile = getGcFile();
        if (!gcFile.exists()) {
            if (!gcFile.getParentFile().exists()) {
                return false;
            } else {
                lastCleanupTime = null;
            }
        } else {
            lastCleanupTime = Instant.ofEpochMilli(gcFile.lastModified());
        }

        return cleanupFrequency.requiresCleanup(lastCleanupTime);
    }

    private void markCleanedUp() {
        GFileUtils.touch(getGcFile());
    }

    private File getGcFile() {
        File currentVersionCacheDir = versionSpecificCacheDirectoryScanner.getDirectory(GradleVersion.current());
        return new File(currentVersionCacheDir, "gc.properties");
    }

    private void performCleanup(CleanupProgressMonitor progressMonitor) {
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = scanForVersionSpecificCacheDirs();
        for (GradleVersion baseVersion : cacheDirsByBaseVersion.keySet()) {
            performCleanup(cacheDirsByBaseVersion.get(baseVersion), releaseTimestampSupplier, snapshotTimestampSupplier, progressMonitor);
        }
        markCleanedUp();
    }

    private SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> scanForVersionSpecificCacheDirs() {
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = TreeMultimap.create();
        for (VersionSpecificCacheDirectory cacheDir : versionSpecificCacheDirectoryScanner.getExistingDirectories()) {
            cacheDirsByBaseVersion.put(cacheDir.getVersion().getBaseVersion(), cacheDir);
        }
        return cacheDirsByBaseVersion;
    }

    private void performCleanup(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, Supplier<Long> releaseTimestampSupplier, Supplier<Long> snapshotTimestampSupplier, CleanupProgressMonitor progressMonitor) {
        Spec<VersionSpecificCacheDirectory> cleanupCondition = new CleanupCondition(cacheDirsWithSameBaseVersion, releaseTimestampSupplier, snapshotTimestampSupplier);
        for (VersionSpecificCacheDirectory cacheDir : cacheDirsWithSameBaseVersion) {
            if (cleanupCondition.isSatisfiedBy(cacheDir)) {
                progressMonitor.incrementDeleted();
                try {
                    deleteCacheDir(cacheDir.getDir());
                } catch (Exception e) {
                    LOGGER.error("Failed to process/clean up version-specific cache directory: {}", cacheDir.getDir(), e);
                }
            } else {
                progressMonitor.incrementSkipped();
            }
        }
    }

    private void deleteCacheDir(File cacheDir) throws IOException {
        LOGGER.debug("Deleting version-specific cache directory at {}", cacheDir);
        deleter.deleteRecursively(cacheDir);
    }

    private static class CleanupCondition implements Spec<VersionSpecificCacheDirectory> {
        private final SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion;
        private final Supplier<Long> releaseTimestampSupplier;
        private final Supplier<Long> snapshotTimestampSupplier;

        CleanupCondition(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, Supplier<Long> releaseTimestampSupplier, Supplier<Long> snapshotTimestampSupplier) {
            this.cacheDirsWithSameBaseVersion = cacheDirsWithSameBaseVersion;
            this.releaseTimestampSupplier = releaseTimestampSupplier;
            this.snapshotTimestampSupplier = snapshotTimestampSupplier;
        }

        @Override
        public boolean isSatisfiedBy(VersionSpecificCacheDirectory cacheDir) {
            if (cacheDir.getVersion().compareTo(GradleVersion.current()) >= 0) {
                return false;
            }
            File markerFile = new File(cacheDir.getDir(), MARKER_FILE_PATH);
            return markerFile.exists() && markerFileHasNotBeenTouchedRecently(cacheDir, markerFile);
        }

        private boolean markerFileHasNotBeenTouchedRecently(VersionSpecificCacheDirectory cacheDir, File markerFile) {
            if (markerFile.lastModified() < releaseTimestampSupplier.get()) {
                return true;
            }
            if (cacheDir.getVersion().isSnapshot() && markerFile.lastModified() < snapshotTimestampSupplier.get()) {
                // Keep at least one snapshot version for this base version
                return cacheDirsWithSameBaseVersion.tailSet(cacheDir).size() > 1;
            }
            return false;
        }
    }
}
