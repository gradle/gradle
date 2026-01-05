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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.file.FileAccessTimeJournal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

import static org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter;

public class FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup implements CleanupAction {

    private final int cacheDepth;
    private final FileAccessTimeJournal journal;
    private final Supplier<Long> removeUnusedEntriesOlderThan;

    public FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup(int cacheDepth, FileAccessTimeJournal journal, Supplier<Long> removeUnusedEntriesOlderThan) {
        this.cacheDepth = cacheDepth;
        this.journal = journal;
        this.removeUnusedEntriesOlderThan = removeUnusedEntriesOlderThan;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        Preconditions.checkArgument(cleanableStore instanceof FineGrainedPersistentCache, "Expected a FineGrainedPersistentCache but got: %s", cleanableStore.getClass());

        FineGrainedPersistentCache cache = (FineGrainedPersistentCache) cleanableStore;
        MarkAndSweepCacheEntrySoftDeleter softDeleter = (MarkAndSweepCacheEntrySoftDeleter) getSoftDeleter(cache);
        CleanupAction cleanupAction = new MarkAndSweepCleanupAction(cache, cacheDepth, journal, removeUnusedEntriesOlderThan, softDeleter);
        CleanableStore decoratedCleanableStore = new CleanableStore() {
            @Override
            public File getBaseDir() {
                return cleanableStore.getBaseDir();
            }

            @Override
            public Collection<File> getReservedCacheFiles() {
                return ImmutableList.<File>builder()
                    .addAll(cleanableStore.getReservedCacheFiles())
                    .add(softDeleter.getGcDir())
                    .build();
            }

            @Override
            public String getDisplayName() {
                return cleanableStore.getDisplayName();
            }
        };
        cleanupAction.clean(decoratedCleanableStore, progressMonitor);
    }

    public FineGrainedCacheEntrySoftDeleter getSoftDeleter(FineGrainedPersistentCache cache) {
        return new MarkAndSweepCacheEntrySoftDeleter(cache);
    }

    private static class MarkAndSweepCleanupAction extends LeastRecentlyUsedCacheCleanup {

        private static final Duration SOFT_DELETION_DURATION = Duration.ofHours(6);

        private final FineGrainedPersistentCache cache;
        private final MarkAndSweepCacheEntrySoftDeleter softDeleter;
        private final Supplier<Long> removeUnusedEntriesOlderThan;

        public MarkAndSweepCleanupAction(
            FineGrainedPersistentCache cache,
            int cacheDepth,
            FileAccessTimeJournal journal,
            Supplier<Long> removeUnusedEntriesOlderThan,
            MarkAndSweepCacheEntrySoftDeleter softDeleter
        ) {
            super(new SingleDepthFilesFinder(cacheDepth),  journal, removeUnusedEntriesOlderThan);
            this.cache = cache;
            this.softDeleter = softDeleter;
            this.removeUnusedEntriesOlderThan = removeUnusedEntriesOlderThan;
        }

        @Override
        protected boolean doDeletion(File file) {
            validatePath(file);
            String key = pathToCacheKey(file);
            return cache.useCache(pathToCacheKey(file), () -> {
                if (shouldBeSoftDeleted(key)) {
                    softDeleter.softDelete(key);
                    return false;
                } else if (shouldBeHardDeleted(key)) {
                    return hardDelete(file, key);
                }
                return false;
            });
        }

        private void validatePath(File file) {
            if (!file.toPath().startsWith(cache.getBaseDir().toPath())) {
                throw new IllegalStateException(String.format("Cannot delete '%s' as it's not a subpath of cache at '%s'.", file.getAbsolutePath(), cache.getBaseDir().getAbsolutePath()));
            }
        }

        private boolean hardDelete(File file, String key) {
            try {
                // First delete content
                FileUtils.cleanDirectory(file);
            } catch (IOException e) {
                return false;
            }
            // Then delete soft deletion markers
            softDeleter.removeSoftDeleteMarker(key);
            softDeleter.removeSoftGcFile(key);
            // After delete directory
            // TODO, delete file lock
            return FileUtils.deleteQuietly(file);
        }

        private String pathToCacheKey(File file) {
            Path relativized = cache.getBaseDir().toPath().relativize(file.toPath());
            return relativized.toString();
        }

        private boolean shouldBeSoftDeleted(String key) {
            if (softDeleter.isSoftDeleted(key)) {
                return false;
            }
            // Write job can unmark entry as soft deleted. To avoid potential "soft delete, soft undelete, soft delete, soft undelete cycle",
            // we have a soft gc file that has last soft deletion time in case the entry was not hard deleted but just soft deleted and then soft undeleted via some write job.
            long lastSoftDeleteTime = softDeleter.getSoftGcFile(key).lastModified();
            return lastSoftDeleteTime < removeUnusedEntriesOlderThan.get();
        }

        private boolean shouldBeHardDeleted(String key) {
            return softDeleter.isSoftDeleted(key) && (Instant.now().toEpochMilli() - softDeleter.getSoftDeleteFile(key).lastModified()) >= SOFT_DELETION_DURATION.toMillis();
        }
    }

    private static class MarkAndSweepCacheEntrySoftDeleter implements FineGrainedCacheEntrySoftDeleter {

        private final File gcDir;

        public MarkAndSweepCacheEntrySoftDeleter(FineGrainedPersistentCache cache) {
            this.gcDir = new File(cache.getBaseDir(), "gc");
        }

        @Override
        public boolean isSoftDeleted(String key) {
            return getSoftDeleteFile(key).exists();
        }

        @Override
        public void removeSoftDeleteMarker(String key) {
            getSoftDeleteFile(key).delete();
        }

        public void removeSoftGcFile(String key) {
            getSoftGcFile(key).delete();
        }

        public void softDelete(String key) {
            gcDir.mkdirs();
            touch(getSoftDeleteFile(key));
            touch(getSoftGcFile(key));
        }

        public File getGcDir() {
            return gcDir;
        }

        private File getSoftDeleteFile(String key) {
            return new File(gcDir, key + ".soft.deleted");
        }

        private File getSoftGcFile(String key) {
            return new File(gcDir, key + ".soft.gc");
        }

        @SuppressWarnings("MethodMayBeStatic")
        private void touch(File file) {
            try {
                FileUtils.touch(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
