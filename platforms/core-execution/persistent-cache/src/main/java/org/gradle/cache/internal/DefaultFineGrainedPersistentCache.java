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

import com.google.common.collect.ImmutableSet;
import com.google.common.math.IntMath;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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

    private final ProducerGuard<Object> guard = ProducerGuard.adaptive();
    private final StripedFileLockAccess<LockOnDemandEagerReleaseCrossProcessCacheAccess> fileLocks;
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
        this.fileLocks = createLocks(numberOfLocks, fileLockManager);
        this.gcFile = new File(baseDir, "gc.properties");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cleanupStrategy.apply(this));
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
            .addAll(fileLocks.fileLocks)
            .add(gcFile)
            .build();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T useCache(String key, Supplier<? extends T> action) {
        // Make sure the key that is actually a path never ends with /, because we could lock the same path with different locks
        checkArgument(!key.endsWith("/") && !key.endsWith("\\"), "Cache key '%s' must not end with a slash", key);
        return guard.guardByKey(key, () -> withFileLock(key, action));
    }

    @Override
    public void useCache(String key, Runnable action) {
        useCache(key, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withFileLock(String key, Supplier<? extends T> action) {
        return fileLocks.get(key).withFileLock(action);
    }

    @Override
    public synchronized void close() {
        if (!alreadyCleaned.get()) {
            cleanup();
        }
        fileLocks.close();
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

    private StripedFileLockAccess<LockOnDemandEagerReleaseCrossProcessCacheAccess> createLocks(int numberOfLocks, FileLockManager fileLockManager) {
        return StripedFileLockAccess.custom(
            numberOfLocks,
            baseDir,
            fileLock -> new LockOnDemandEagerReleaseCrossProcessCacheAccess(
                getDisplayName(),
                fileLock,
                DefaultLockOptions.mode(Exclusive),
                fileLockManager,
                new ReentrantLock(),
                CacheInitializationAction.NO_INIT_REQUIRED,
                lock -> {},
                lock -> {}
            )
        );
    }

    /**
     * A striped cache access that allows for concurrent access to file locks.
     *
     * This class is based on Guava's <a href="https://github.com/google/guava/blob/995f5d428dc003d7b7887c04398cd7e6f6610461/guava/src/com/google/common/util/concurrent/Striped.java#L371">Striped</a>
     * implementation.
     */
    private static class StripedFileLockAccess<T extends Closeable> implements Closeable {

        private final Closeable[] array;
        private final List<File> fileLocks;
        private final int mask;

        private StripedFileLockAccess(int stripes, File baseDir, Function<File, T> supplier) {
            checkArgument(stripes > 0 && stripes <= MAX_NUMBER_OF_LOCKS, "Stripes must be positive and <= %s, but was: %s", MAX_NUMBER_OF_LOCKS, stripes);
            this.mask = ceilToPowerOfTwo(stripes) - 1;
            this.array = new Closeable[stripes + 1];
            this.fileLocks = new ArrayList<>(stripes);
            for (int i = 0; i < array.length; i++) {
                File lockFile = new File(baseDir, baseDir.getName() + "-lock-" + i + ".lock");
                fileLocks.add(lockFile);
                array[i] = supplier.apply(lockFile);
            }
        }

        private static int ceilToPowerOfTwo(int x) {
            return 1 << IntMath.log2(x, RoundingMode.CEILING);
        }

        @SuppressWarnings("unchecked")
        public T get(String key) {
            return (T) array[indexFor(key)];
        }

        private int indexFor(Object key) {
            int hash = smear(key.hashCode());
            return hash & mask;
        }

        private static int smear(int hashCode) {
            hashCode ^= (hashCode >>> 20) ^ (hashCode >>> 12);
            return hashCode ^ (hashCode >>> 7) ^ (hashCode >>> 4);
        }

        public static <T extends Closeable> StripedFileLockAccess<T> custom(int stripes, File baseDir, Function<File, T> supplier) {
            return new StripedFileLockAccess<>(stripes, baseDir, supplier);
        }

        @Override
        public void close() {
            CompositeStoppable.stoppable((Object[]) array).stop();
        }
    }
}
