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
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache.FILE_HASHES_CACHE_KEY;

public class VersionSpecificCacheCleanupAction {

    @VisibleForTesting static final String MARKER_FILE_PATH = FILE_HASHES_CACHE_KEY + "/" + FILE_HASHES_CACHE_KEY + ".lock";
    private static final Logger LOGGER = Logging.getLogger(VersionSpecificCacheCleanupAction.class);
    private static final long CLEANUP_INTERVAL_IN_HOURS = 24;
    private static final long CLEANUP_TIMEOUT_MILLIS = 10000;

    private final VersionSpecificCacheDirectoryScanner versionSpecificCacheDirectoryScanner;
    private final long maxUnusedDaysForReleases;
    private final long maxUnusedDaysForSnapshots;

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, long maxUnusedDaysForReleasesAndSnapshots) {
        this(cacheBaseDir, maxUnusedDaysForReleasesAndSnapshots, maxUnusedDaysForReleasesAndSnapshots);
    }

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, long maxUnusedDaysForReleases, long maxUnusedDaysForSnapshots) {
        Preconditions.checkArgument(maxUnusedDaysForReleases >= maxUnusedDaysForSnapshots,
            "maxUnusedDaysForReleases (%s) must be greater than or equal to maxUnusedDaysForSnapshots (%s)", maxUnusedDaysForReleases, maxUnusedDaysForSnapshots);
        this.versionSpecificCacheDirectoryScanner = new VersionSpecificCacheDirectoryScanner(cacheBaseDir);
        this.maxUnusedDaysForReleases = maxUnusedDaysForReleases;
        this.maxUnusedDaysForSnapshots = maxUnusedDaysForSnapshots;
    }

    public void execute() {
        if (requiresCleanup()) {
            CountdownTimer timer = Time.startCountdownTimer(CLEANUP_TIMEOUT_MILLIS);
            performCleanup(timer);
            LOGGER.debug("Processed version-specific caches for cleanup in {}", timer.getElapsed());
        }
    }

    private boolean requiresCleanup() {
        File gcFile = getGcFile();
        if (!gcFile.exists()) {
            return gcFile.getParentFile().exists();
        }
        long duration = System.currentTimeMillis() - gcFile.lastModified();
        long timeInHours = TimeUnit.MILLISECONDS.toHours(duration);
        return timeInHours >= CLEANUP_INTERVAL_IN_HOURS;
    }

    private void markCleanedUp() {
        GFileUtils.touch(getGcFile());
    }

    private File getGcFile() {
        File currentVersionCacheDir = versionSpecificCacheDirectoryScanner.getDirectory(GradleVersion.current());
        return new File(currentVersionCacheDir, "gc.properties");
    }

    @VisibleForTesting
    protected void performCleanup(CountdownTimer timer) {
        MinimumTimestampProvider minimumTimestampProvider = new MinimumTimestampProvider();
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = scanForVersionSpecificCacheDirs();
        boolean completelyCleanedUp = true;
        for (GradleVersion baseVersion : cacheDirsByBaseVersion.keySet()) {
            completelyCleanedUp = performCleanup(cacheDirsByBaseVersion.get(baseVersion), timer, minimumTimestampProvider);
            if (!completelyCleanedUp) {
                break;
            }
        }
        if (completelyCleanedUp) {
            markCleanedUp();
        }
    }

    private SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> scanForVersionSpecificCacheDirs() {
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = TreeMultimap.create();
        for (VersionSpecificCacheDirectory cacheDir : versionSpecificCacheDirectoryScanner.getExistingDirectories()) {
            cacheDirsByBaseVersion.put(cacheDir.getVersion().getBaseVersion(), cacheDir);
        }
        return cacheDirsByBaseVersion;
    }

    private boolean performCleanup(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, CountdownTimer timer, MinimumTimestampProvider minimumTimestampProvider) {
        Spec<VersionSpecificCacheDirectory> cleanupCondition = new CleanupCondition(cacheDirsWithSameBaseVersion, minimumTimestampProvider);
        for (VersionSpecificCacheDirectory cacheDir : cacheDirsWithSameBaseVersion) {
            if (timer.hasExpired()) {
                return false;
            }
            if (cleanupCondition.isSatisfiedBy(cacheDir)) {
                try {
                    deleteCacheDir(cacheDir.getDir());
                } catch (Exception e) {
                    LOGGER.error("Failed to process/clean up version-specific cache directory: {}", cacheDir.getDir(), e);
                }
            }
        }
        return true;
    }

    private void deleteCacheDir(File cacheDir) throws IOException {
        LOGGER.debug("Deleting version-specific cache directory at {}", cacheDir);
        FileUtils.deleteDirectory(cacheDir);
    }

    private static class CleanupCondition implements Spec<VersionSpecificCacheDirectory> {
        private final SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion;
        private final MinimumTimestampProvider minimumTimestampProvider;

        CleanupCondition(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, MinimumTimestampProvider minimumTimestampProvider) {
            this.cacheDirsWithSameBaseVersion = cacheDirsWithSameBaseVersion;
            this.minimumTimestampProvider = minimumTimestampProvider;
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
            if (markerFile.lastModified() < minimumTimestampProvider.forReleases()) {
                return true;
            }
            if (cacheDir.getVersion().isSnapshot() && markerFile.lastModified() < minimumTimestampProvider.forSnapshots()) {
                return cacheDirsWithSameBaseVersion.tailSet(cacheDir).size() > 1;
            }
            return false;
        }
    }

    private class MinimumTimestampProvider {
        private final long minimumReleaseTimestamp;
        private final long minimumSnapshotTimestamp;

        MinimumTimestampProvider() {
            long startTime = System.currentTimeMillis();
            this.minimumReleaseTimestamp = compute(startTime, maxUnusedDaysForReleases);
            this.minimumSnapshotTimestamp = compute(startTime, maxUnusedDaysForSnapshots);
        }

        private long compute(long startTime, long maxUnusedDays) {
            return Math.max(0, startTime - TimeUnit.DAYS.toMillis(maxUnusedDays));
        }

        long forReleases() {
            return minimumReleaseTimestamp;
        }

        long forSnapshots() {
            return minimumSnapshotTimestamp;
        }
    }
}
