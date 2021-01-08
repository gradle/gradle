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
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

public class VersionSpecificCacheCleanupAction implements DirectoryCleanupAction {
    private final static String FILE_HASHES_CACHE_KEY =  CrossBuildFileHashCache.Kind.FILE_HASHES.getCacheId();

    @VisibleForTesting static final String MARKER_FILE_PATH = FILE_HASHES_CACHE_KEY + "/" + FILE_HASHES_CACHE_KEY + ".lock";
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionSpecificCacheCleanupAction.class);
    private static final long CLEANUP_INTERVAL_IN_HOURS = 24;

    private final VersionSpecificCacheDirectoryScanner versionSpecificCacheDirectoryScanner;
    private final long maxUnusedDaysForReleases;
    private final long maxUnusedDaysForSnapshots;
    private final Deleter deleter;

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, long maxUnusedDaysForReleasesAndSnapshots, Deleter deleter) {
        this(cacheBaseDir, maxUnusedDaysForReleasesAndSnapshots, maxUnusedDaysForReleasesAndSnapshots, deleter);
    }

    public VersionSpecificCacheCleanupAction(File cacheBaseDir, long maxUnusedDaysForReleases, long maxUnusedDaysForSnapshots, Deleter deleter) {
        this.deleter = deleter;
        Preconditions.checkArgument(maxUnusedDaysForReleases >= maxUnusedDaysForSnapshots,
            "maxUnusedDaysForReleases (%s) must be greater than or equal to maxUnusedDaysForSnapshots (%s)", maxUnusedDaysForReleases, maxUnusedDaysForSnapshots);
        this.versionSpecificCacheDirectoryScanner = new VersionSpecificCacheDirectoryScanner(cacheBaseDir);
        this.maxUnusedDaysForReleases = maxUnusedDaysForReleases;
        this.maxUnusedDaysForSnapshots = maxUnusedDaysForSnapshots;
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

    private void performCleanup(CleanupProgressMonitor progressMonitor) {
        MinimumTimestampProvider minimumTimestampProvider = new MinimumTimestampProvider();
        SortedSetMultimap<GradleVersion, VersionSpecificCacheDirectory> cacheDirsByBaseVersion = scanForVersionSpecificCacheDirs();
        for (GradleVersion baseVersion : cacheDirsByBaseVersion.keySet()) {
            performCleanup(cacheDirsByBaseVersion.get(baseVersion), minimumTimestampProvider, progressMonitor);
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

    private void performCleanup(SortedSet<VersionSpecificCacheDirectory> cacheDirsWithSameBaseVersion, MinimumTimestampProvider minimumTimestampProvider, CleanupProgressMonitor progressMonitor) {
        Spec<VersionSpecificCacheDirectory> cleanupCondition = new CleanupCondition(cacheDirsWithSameBaseVersion, minimumTimestampProvider);
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
