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

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.os.OperatingSystem;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter;
import static org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.SOFT_DELETION_DURATION;
import static org.gradle.cache.FineGrainedPersistentCache.INTERNAL_DIR_PATH;
import static org.gradle.cache.FineGrainedPersistentCache.LOCKS_DIR_RELATIVE_PATH;

@NullMarked
public class FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup implements CleanupAction {

    private final FileAccessTimeJournal journal;
    private final Supplier<Long> removeUnusedEntriesOlderThan;

    public FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup(FileAccessTimeJournal journal, Supplier<Long> removeUnusedEntriesOlderThan) {
        this.journal = journal;
        this.removeUnusedEntriesOlderThan = removeUnusedEntriesOlderThan;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        checkArgument(cleanableStore instanceof FineGrainedPersistentCache, "Expected a FineGrainedPersistentCache but got: %s", cleanableStore.getClass());

        FineGrainedPersistentCache cache = (FineGrainedPersistentCache) cleanableStore;
        MarkAndSweepCacheEntrySoftDeleter softDeleter = (MarkAndSweepCacheEntrySoftDeleter) getSoftDeleter(cache);
        CleanupAction cleanupAction = new MarkAndSweepCleanupAction(cache, journal, removeUnusedEntriesOlderThan, softDeleter);
        cleanupAction.clean(cleanableStore, progressMonitor);
    }

    public FineGrainedCacheEntrySoftDeleter getSoftDeleter(FineGrainedPersistentCache cache) {
        return new MarkAndSweepCacheEntrySoftDeleter(cache);
    }

    private static class MarkAndSweepCleanupAction extends LeastRecentlyUsedCacheCleanup {

        private final FineGrainedPersistentCache cache;
        private final MarkAndSweepCacheEntrySoftDeleter softDeleter;
        private final Supplier<Long> removeUnusedEntriesOlderThan;

        public MarkAndSweepCleanupAction(
            FineGrainedPersistentCache cache,
            FileAccessTimeJournal journal,
            Supplier<Long> removeUnusedEntriesOlderThan,
            MarkAndSweepCacheEntrySoftDeleter softDeleter
        ) {
            super(new SingleDepthFilesFinder(1),  journal, removeUnusedEntriesOlderThan);
            this.cache = cache;
            this.softDeleter = softDeleter;
            this.removeUnusedEntriesOlderThan = removeUnusedEntriesOlderThan;
        }

        @Override
        public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
            // First perform regular mark-and-sweep cleanup for entries
            super.clean(cleanableStore, progressMonitor);
            // Then clean up any orphaned locks and GC dirs
            cleanupOrphanGcDirsAndFileLocks();
        }

        @Override
        protected boolean doDeletion(File path) {
            String key = getPathAsCacheKey(path);
            if (!shouldBeSoftDeleted(key) && !shouldBeHardDeleted(key)) {
                return false;
            }
            return cache.useCache(key, () -> {
                if (shouldBeSoftDeleted(key)) {
                    softDeleter.softDelete(key);
                    return false;
                } else if (shouldBeHardDeleted(key)) {
                    return hardDelete(path, key);
                }
                return false;
            });
        }

        private boolean shouldBeSoftDeleted(String key) {
            if (softDeleter.isSoftDeleted(key)) {
                return false;
            }
            // Write job can unmark entry as soft deleted. To avoid potential "soft delete, soft undelete, soft delete, soft undelete" cycle,
            // we have a gc file that has last soft deletion time we check here.
            long lastSoftDeleteTime = softDeleter.getSoftGcFile(key).lastModified();
            return lastSoftDeleteTime < removeUnusedEntriesOlderThan.get();
        }

        private boolean shouldBeHardDeleted(String key) {
            return softDeleter.isSoftDeleted(key) && (Instant.now().toEpochMilli() - softDeleter.getSoftDeleteMarker(key).lastModified()) >= SOFT_DELETION_DURATION.toMillis();
        }

        private boolean hardDelete(File path, String key) {
            // First delete cache entry
            FileUtils.deleteQuietly(path);
            if (!path.exists()) {
                // Then delete gc information
                FileUtils.deleteQuietly(softDeleter.getKeyGcDir(key));
                // And finally delete also a lock file
                FileUtils.deleteQuietly(getLockFile(key));
                return true;
            }
            return false;
        }

        private void cleanupOrphanGcDirsAndFileLocks() {
            Collection<File> reservedCacheFiles = cache.getReservedCacheFiles();
            Set<String> entryKeys = listEntryKeys(cache.getBaseDir(),
                file -> !reservedCacheFiles.contains(file),
                Function.identity()
            );
            Set<String> locksKeys = listEntryKeys(getLocksDir(),
                file -> file.getName().endsWith(".lock"),
                name -> name.substring(0, name.lastIndexOf(".lock"))
            );
            Set<String> gcDirsKeys = listEntryKeys(softDeleter.getGcDir(), File::isDirectory, Function.identity());

            Set<String> orphanKeys = Sets.difference(Sets.union(locksKeys, gcDirsKeys), entryKeys);
            orphanKeys.forEach(this::deleteOrphanKey);
        }

        private void deleteOrphanKey(String key) {
            File baseDir = cache.getBaseDir();
            File lockFile = getLockFile(key);
            File gcDir = softDeleter.getKeyGcDir(key);
            File cacheEntry = new File(baseDir, key);
            if (OperatingSystem.current().isWindows()) {
                if (gcDir.exists()) {
                    cache.useCache(key, () -> {
                        if (!cacheEntry.exists()) {
                            FileUtils.deleteQuietly(gcDir);
                        }
                    });
                }
                // On Windows we cannot delete an opened file,
                // but that has also a nice consequence that we can just try to delete a lock file
                FileUtils.deleteQuietly(lockFile);
            } else {
                cache.useCache(key, () -> {
                    if (!cacheEntry.exists()) {
                        FileUtils.deleteQuietly(gcDir);
                        FileUtils.deleteQuietly(lockFile);
                    }
                });
            }
        }

        private static Set<String> listEntryKeys(File root, Predicate<File> filter, Function<String, String> mapper) {
            if (!root.exists()) {
                return Collections.emptySet();
            }
            try (Stream<Path> stream = Files.list(root.toPath())) {
                return stream
                    .map(Path::toFile)
                    .filter(filter)
                    .map(path -> mapper.apply(path.getName()))
                    .collect(Collectors.toSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private File getLocksDir() {
            return new File(cache.getBaseDir(), LOCKS_DIR_RELATIVE_PATH);
        }

        private File getLockFile(String key) {
            return new File(cache.getBaseDir(), LOCKS_DIR_RELATIVE_PATH + "/" + key + ".lock");
        }

        private String getPathAsCacheKey(File path) {
            checkArgument(path.getParentFile().equals(cache.getBaseDir()),
                "Expected a path '%s' to be a direct child of cache at '%s'.", path.getAbsolutePath(), cache.getBaseDir().getAbsolutePath());
            return path.getName();
        }
    }

    private static class MarkAndSweepCacheEntrySoftDeleter implements FineGrainedCacheEntrySoftDeleter {

        private final File gcDir;

        public MarkAndSweepCacheEntrySoftDeleter(FineGrainedPersistentCache cache) {
            this.gcDir = new File(cache.getBaseDir(), INTERNAL_DIR_PATH + "/gc");
        }

        @Override
        public boolean isSoftDeleted(String key) {
            return getSoftDeleteMarker(key).exists();
        }

        @Override
        public void removeSoftDeleteMarker(String key) {
            getSoftDeleteMarker(key).delete();
        }

        public void softDelete(String key) {
            touch(getSoftDeleteMarker(key));
            touch(getSoftGcFile(key));
        }

        public File getKeyGcDir(String key) {
            return new File(gcDir, key);
        }

        public File getGcDir() {
            return gcDir;
        }

        private File getSoftDeleteMarker(String key) {
            return new File(getKeyGcDir(key), "soft.deleted");
        }

        private File getSoftGcFile(String key) {
            return new File(getKeyGcDir(key), "gc.properties");
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
