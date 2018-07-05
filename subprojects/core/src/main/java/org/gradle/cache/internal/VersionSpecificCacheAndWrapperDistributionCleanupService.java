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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache.FILE_HASHES_CACHE_KEY;

public class VersionSpecificCacheAndWrapperDistributionCleanupService implements Stoppable {

    @VisibleForTesting static final String MARKER_FILE_PATH = FILE_HASHES_CACHE_KEY + "/" + FILE_HASHES_CACHE_KEY + ".lock";
    @VisibleForTesting static final String WRAPPER_DISTRIBUTION_FILE_PATH = "wrapper/dists";
    private static final Logger LOGGER = Logging.getLogger(VersionSpecificCacheAndWrapperDistributionCleanupService.class);
    private static final long CLEANUP_INTERVAL_IN_HOURS = 24;
    private static final long CLEANUP_TIMEOUT_MILLIS = 10000;
    private static final long MAX_UNUSED_DAYS_FOR_RELEASES = 30;
    private static final long MAX_UNUSED_DAYS_FOR_SNAPSHOTS = 7;
    private static final ImmutableList<String> DISTRIBUTION_TYPES = ImmutableList.of("bin", "all");

    private final GradleVersion currentVersion;
    private final VersionSpecificCacheDirectoryService versionSpecificCacheDirectoryService;
    private final File distsDir;

    public VersionSpecificCacheAndWrapperDistributionCleanupService(GradleVersion currentVersion, VersionSpecificCacheDirectoryService versionSpecificCacheDirectoryService, File gradleUserHomeDirectory) {
        this.currentVersion = currentVersion;
        this.versionSpecificCacheDirectoryService = versionSpecificCacheDirectoryService;
        this.distsDir = new File(gradleUserHomeDirectory, WRAPPER_DISTRIBUTION_FILE_PATH);
    }

    @Override
    public void stop() {
        if (requiresCleanup()) {
            CountdownTimer timer = Time.startCountdownTimer(CLEANUP_TIMEOUT_MILLIS);
            performCleanup(timer);
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
        File currentVersionCacheDir = versionSpecificCacheDirectoryService.getDirectory(GradleVersion.current());
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
        LOGGER.debug("Processed version-specific caches for cleanup in {}", timer.getElapsed());
    }

    private SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> scanForVersionSpecificCacheDirs() {
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = TreeMultimap.create();
        for (VersionSpecificCacheDirectory cacheDir : versionSpecificCacheDirectoryService.getExistingDirectories()) {
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
                    deleteDistributions(cacheDir.getVersion());
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

    private void deleteDistributions(GradleVersion version) throws IOException {
        for (String distributionType : DISTRIBUTION_TYPES) {
            File dir = new File(distsDir, "gradle-" + version.getVersion() + "-" + distributionType);
            if (dir.isDirectory()) {
                LOGGER.debug("Deleting Gradle distribution at {}", dir);
                FileUtils.deleteDirectory(dir);
            }
        }
    }

    private class CleanupCondition implements Spec<VersionSpecificCacheDirectory> {
        private final SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion;
        private final MinimumTimestampProvider minimumTimestampProvider;

        CleanupCondition(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, MinimumTimestampProvider minimumTimestampProvider) {
            this.cacheDirsWithSameBaseVersion = cacheDirsWithSameBaseVersion;
            this.minimumTimestampProvider = minimumTimestampProvider;
        }

        @Override
        public boolean isSatisfiedBy(VersionSpecificCacheDirectory cacheDir) {
            if (cacheDir.getVersion().compareTo(currentVersion) >= 0) {
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

    private static class MinimumTimestampProvider {
        private final long minimumReleaseTimestamp;
        private final long minimumSnapshotTimestamp;

        MinimumTimestampProvider() {
            long startTime = System.currentTimeMillis();
            this.minimumReleaseTimestamp = compute(startTime, MAX_UNUSED_DAYS_FOR_RELEASES);
            this.minimumSnapshotTimestamp = compute(startTime, MAX_UNUSED_DAYS_FOR_SNAPSHOTS);
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
