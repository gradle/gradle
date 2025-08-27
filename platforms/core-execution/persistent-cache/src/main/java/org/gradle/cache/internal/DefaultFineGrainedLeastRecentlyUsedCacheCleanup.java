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

import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.cache.FineGrainedCacheCleanupStrategy.FineGrainedCacheMarkAndSweepDeleter;
import org.gradle.cache.FineGrainedLeastRecentlyUsedCacheCleanup;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileAccessTimeJournal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DefaultFineGrainedLeastRecentlyUsedCacheCleanup extends LeastRecentlyUsedCacheCleanup implements FineGrainedLeastRecentlyUsedCacheCleanup {

    private final FineGrainedPersistentCache cache;
    private final FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter deleter;

    public DefaultFineGrainedLeastRecentlyUsedCacheCleanup(FineGrainedPersistentCache cache, FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter deleter, int cacheDepth, FileAccessTimeJournal journal, Supplier<Long> removeUnusedEntriesOlderThan) {
        super(new SingleDepthFilesFinder(cacheDepth), journal, removeUnusedEntriesOlderThan);
        this.cache = cache;
        this.deleter = deleter;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        super.clean(cache, progressMonitor);
    }

    @Override
    protected boolean doDelete(File file) {
        if (!deleter.isStale(file)) {
            // If the entry is not stale, we add a stale marker to it
            // to indicate that it should be cleaned next time if still stale.
            deleter.addStaleMarker(file);
            return false;
        } else if (deleter.shouldBeDeleted(file)) {
            return deleter.withDeletionLock(asKey(file), () -> {
                // We need to recheck if the entry is still stale
                // or some other process deleted it/recreated it before we acquired the lock.
                if (deleter.isStale(file)) {
                    deleter.delete(file);
                    return true;
                }
                return false;
            });
        } else {
            return false;
        }
    }

    private String asKey(File file) {
        Path relativized = cache.getBaseDir().toPath().relativize(file.toPath());
        return relativized.toString();
    }

    public static class FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter implements FineGrainedCacheMarkAndSweepDeleter {

        private final static Duration STALE_DURATION = Duration.ofHours(1);
        private final Deleter deleter;
        private final FineGrainedPersistentCache cache;

        public FineGrainedCacheLeastRecentlyUsedMarkAndSweepDeleter(FineGrainedPersistentCache cache, Deleter deleter) {
            this.deleter = deleter;
            this.cache = cache;
        }

        @Override
        public void unstale(File entry) {
            try {
                deleter.deleteRecursively(getStaleMarkerFile(entry));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean isStale(File entry) {
            return getStaleMarkerFile(entry).exists();
        }

        @Override
        public boolean delete(File entry) {
            if (!entry.exists()) {
                return false;
            }
            Path staleMarker = getStaleMarkerFile(entry).toPath();
            try (Stream<Path> stream = Files.find(entry.toPath(), 1, (path, basicFileAttributes) -> !path.equals(staleMarker))) {
                Iterator<Path> iterator = stream.iterator();
                while (iterator.hasNext()) {
                    deleter.deleteRecursively(iterator.next().toFile());
                }
                unstale(entry);
                deleter.deleteRecursively(entry);
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public <T> T withDeletionLock(String path, Supplier<T> supplier) {
            return cache.useCache(path, "-deletion", supplier);
        }

        private void addStaleMarker(File entry) {
            if (entry.exists()) {
                try {
                    getStaleMarkerFile(entry).createNewFile();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        private boolean shouldBeDeleted(File entry) {
            File staleMarker = getStaleMarkerFile(entry);
            return staleMarker.exists() && (System.currentTimeMillis() - staleMarker.lastModified()) >= STALE_DURATION.toMillis();
        }

        @Override
        public File getStaleMarkerFile(File entry) {
            return new File(entry, ".stale");
        }
    }
}
