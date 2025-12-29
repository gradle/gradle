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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.LockOptions;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private final File gcFile;
    private final CacheCleanupExecutor cleanupExecutor;
    private final File baseDir;
    private final String displayName;
    private final AtomicBoolean alreadyCleaned = new AtomicBoolean();
    private final FileLockManager fileLockManager;

    public DefaultFineGrainedPersistentCache(
        File baseDir,
        String displayName,
        FileLockManager fileLockManager,
        @SuppressWarnings("unused") int numberOfLocks,
        FineGrainedCacheCleanupStrategy cleanupStrategy
    ) {
        this.baseDir = baseDir;
        this.displayName = displayName;
        this.fileLockManager = fileLockManager;
        this.gcFile = new File(baseDir, "gc.properties");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cleanupStrategy.getCleanupStrategy(this));
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
            .add(gcFile)
            .build();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T useCacheWithLockInfo(String key, Function<LockType, ? extends T> action) {
        String normalizedKey = DefaultFineGrainedPersistentCache.normalizeCacheKey(key);
        return guard.guardByKey(normalizedKey, () -> {
            try (@SuppressWarnings("unused") FileLock lock = acquireLock(normalizedKey)) {
                return action.apply(LockType.PRIMARY_LOCK);
            }
        });
    }

    private FileLock acquireLock(String key) {
        FileLock lock = null;
        LockOptions lockOptions = DefaultLockOptions.mode(Exclusive);
        while (lock == null) {
            File lockFile = getLockFile(key, "");
            // Gradle will never create and delete file.lock immediately, so
            // save few cycles on a first use case, by not checking validity of locks if it doesn't exist yet
            boolean shouldCheckLockValidity = lockFile.exists();
            lock = fileLockManager.lock(lockFile, lockOptions, displayName, "");
            if (shouldCheckLockValidity && !lock.isValid()) {
                lock.close();
                lock = null;
            }
        }
        return lock;
    }

    @Override
    public <T> T useCache(String key, Supplier<? extends T> action) {
        return useCacheWithLockInfo(key, lockType -> action.get());
    }

    @Override
    public void useCache(String key, Runnable action) {
        useCacheWithLockInfo(key, lockType -> {
            action.run();
            return null;
        });
    }

    private File getLockFile(String path, String lockSuffix) {
        return new File(baseDir, path + lockSuffix + ".lock");
    }

    @Override
    public synchronized void close() {
        if (!alreadyCleaned.get()) {
            cleanup();
        }
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
}
