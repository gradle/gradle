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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.IntMath;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.cache.FileLockManager.LockMode.Exclusive;

public class DefaultFineGrainedPersistentCache implements FineGrainedPersistentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFineGrainedPersistentCache.class);

    /**
     * The maximum number of locks that can be used for the cache.
     * This limit is set to 512, which is a too big number for most use cases anyway, since process count is usually low.
     *
     * You can increase this limit if you need more locks, but be aware this will mean more lock files in the cache directory,
     */
    public static final int MAX_NUMBER_OF_LOCKS = 512;

    private final ProducerGuard<String> guard = ProducerGuard.adaptive();
    private final StripedLockFileProvider lockFileStripes;
    private final FileLockAccess fileLockAccess;
    private final File gcFile;
    private final CacheCleanupExecutor cleanupExecutor;
    private final File baseDir;
    private final String displayName;
    private final AtomicBoolean alreadyCleaned = new AtomicBoolean();

    public DefaultFineGrainedPersistentCache(
        File baseDir,
        String displayName,
        FileLockManager fileLockManager,
        int numberOfLocks,
        Function<FineGrainedPersistentCache, CacheCleanupStrategy> cleanupStrategy
    ) {
        this.baseDir = baseDir;
        this.displayName = displayName;
        this.lockFileStripes = new StripedLockFileProvider(numberOfLocks, baseDir);
        this.gcFile = new File(baseDir, "gc.properties");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cleanupStrategy.apply(this));
        this.fileLockAccess = new FileLockAccess(displayName, fileLockManager);
    }

    @Override
    public File getCacheDir(String key) {
        return new File(baseDir, key);
    }

    @Override
    public DefaultFineGrainedPersistentCache open() {
        try {
            FileUtils.forceMkdir(baseDir);
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }
        return this;
    }

    @Override
    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public Collection<File> getReservedCacheFiles() {
        return ImmutableSet.<File>builder()
            .addAll(lockFileStripes.getAllPossibleLockFiles())
            .add(gcFile)
            .build();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T useCache(String key, Supplier<? extends T> action) {
        String cacheKey = normalizeCacheKey(key);
        return guard.guardByKey(cacheKey, () -> withFileLock(cacheKey, action));
    }

    @Override
    public void useCache(String key, Runnable action) {
        useCache(key, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withFileLock(String cacheKey, Supplier<? extends T> action) {
        File lockFile = lockFileStripes.getLockFile(cacheKey);
        return fileLockAccess.withFileLock(lockFile, action);
    }

    @Override
    public synchronized void close() {
        if (!alreadyCleaned.get()) {
            cleanup();
        }
        fileLockAccess.close();
    }

    @Override
    public synchronized void cleanup() {
        try {
            alreadyCleaned.set(true);
            cleanupExecutor.cleanup();
        } catch (Exception e) {
            LOGGER.debug("Cache {} could not run cleanup action", displayName);
        }
    }

    public static String normalizeCacheKey(String key) {
        String normalizedKey = FilenameUtils.separatorsToUnix(key);
        Preconditions.checkArgument(!normalizedKey.startsWith("/") && !normalizedKey.endsWith("/"), "Cache key path must be relative and not end with a slash: '%s'", key);
        return normalizedKey;
    }

    /**
     * A striped lock file provider that provides a lock file for a key.
     *
     * This class is based on Guava's <a href="https://github.com/google/guava/blob/995f5d428dc003d7b7887c04398cd7e6f6610461/guava/src/com/google/common/util/concurrent/Striped.java#L371">Striped</a>
     * implementation.
     */
    private static class StripedLockFileProvider {

        private final int mask;
        private final File baseDir;

        private StripedLockFileProvider(int stripes, File baseDir) {
            checkArgument(stripes > 0 && stripes <= MAX_NUMBER_OF_LOCKS, "Stripes must be positive and <= %s, but was: %s", MAX_NUMBER_OF_LOCKS, stripes);
            this.mask = ceilToPowerOfTwo(stripes) - 1;
            this.baseDir = baseDir;
        }

        private static int ceilToPowerOfTwo(int x) {
            return 1 << IntMath.log2(x, RoundingMode.CEILING);
        }

        public List<File> getAllPossibleLockFiles() {
            List<File> lockFiles = new ArrayList<>();
            for (int i = 0; i <= mask; i++) {
                lockFiles.add(getLockFile(i));
            }
            return lockFiles;
        }

        private File getLockFile(int i) {
            return new File(baseDir, baseDir.getName() + "-lock-" + i + ".lock");
        }

        public File getLockFile(String key) {
            int index = indexFor(key);
            return getLockFile(index);
        }

        private int indexFor(String key) {
            int hash = smear(key.hashCode());
            return hash & mask;
        }

        private static int smear(int hashCode) {
            hashCode ^= (hashCode >>> 20) ^ (hashCode >>> 12);
            return hashCode ^ (hashCode >>> 7) ^ (hashCode >>> 4);
        }
    }

    private static class FileLockAccess implements Closeable {

        private final ConcurrentHashMap<File, FileLockReferenceCounter> locks;
        private final FileLockManager fileLockManager;
        private final String displayName;

        public FileLockAccess(String displayName, FileLockManager fileLockManager) {
            this.displayName = displayName;
            this.fileLockManager = fileLockManager;
            this.locks = new ConcurrentHashMap<>();
        }

        public <T> T withFileLock(File lockFile, Supplier<T> factory) {
            locks.compute(lockFile, (key, counter) -> {
                if (counter == null) {
                    FileLock fileLock = fileLockManager.lock(key, DefaultLockOptions.mode(Exclusive), displayName, "");
                    return new FileLockReferenceCounter(fileLock);
                } else {
                    return counter.increaseCount();
                }
            });
            try {
                return factory.get();
            } finally {
                locks.computeIfPresent(lockFile, (key, counter) -> {
                    if (counter.decreaseCount() == 0) {
                        counter.lock.close();
                        return null;
                    } else {
                        return counter;
                    }
                });
            }
        }

        @Override
        public void close() {
            if (!locks.isEmpty()) {
                throw new IllegalStateException("Cannot close FileLockAccess as there are still " + locks.size() + " locks held.");
            }
        }

        private static class FileLockReferenceCounter {
            private final FileLock lock;
            private final AtomicInteger counter;

            public FileLockReferenceCounter(FileLock fileLock) {
                this.lock = fileLock;
                this.counter = new AtomicInteger(1);
            }

            private FileLockReferenceCounter increaseCount() {
                counter.incrementAndGet();
                return this;
            }

            private int decreaseCount() {
                return counter.decrementAndGet();
            }
        }
    }
}
