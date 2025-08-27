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
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FineGrainedPersistentCache;
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
        Function<FineGrainedPersistentCache, CacheCleanupStrategy> cleanupStrategy
    ) {
        this.baseDir = baseDir;
        this.displayName = displayName;
        this.fileLockManager = fileLockManager;
        this.gcFile = new File(baseDir, "gc.properties");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cleanupStrategy.apply(this));
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
    public <T> T useCache(String key, Supplier<? extends T> action) {
        return useCache(key, "", action);
    }

    @Override
    public <T> T useCache(String key, String lockSuffix, Supplier<? extends T> action) {
        String cacheKey = normalizeCacheKey(key);
        return guard.guardByKey(cacheKey, () -> withFileLock(cacheKey, lockSuffix, action));
    }

    @Override
    public void useCache(String key, Runnable action) {
        useCache(key, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public void useCache(String key, String lockSuffix, Runnable action) {
        useCache(key, lockSuffix, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withFileLock(String cacheKey, String lockSuffix, Supplier<? extends T> action) {
        File lockFile = getLockFile(cacheKey, lockSuffix);
        try (@SuppressWarnings("unused") FileLock lock = fileLockManager.lock(lockFile, DefaultLockOptions.mode(Exclusive), displayName, "")) {
            return action.get();
        }
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
