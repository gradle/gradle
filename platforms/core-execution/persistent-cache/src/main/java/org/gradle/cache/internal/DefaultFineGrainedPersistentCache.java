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
import java.util.function.Supplier;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;

/**
 * An implementation of {@link FineGrainedPersistentCache}.
 *
 * It uses exclusive locks for cache entries that are immediately released when action finished.
 * This implementation is suitable for work that writes cache entry only once and reads frequently.
 *
 * Locks are stored in `locks` subdirectory.
 */
public class DefaultFineGrainedPersistentCache implements FineGrainedPersistentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFineGrainedPersistentCache.class);

    private final ProducerGuard<String> guard;
    private final File gcFile;
    private final File locksDir;
    private final CacheCleanupExecutor cleanupExecutor;
    private final File baseDir;
    private final String displayName;
    private final AtomicBoolean alreadyCleaned = new AtomicBoolean();
    private final FileLockManager fileLockManager;

    public DefaultFineGrainedPersistentCache(
        File baseDir,
        String displayName,
        FileLockManager fileLockManager,
        FineGrainedCacheCleanupStrategy cleanupStrategy
    ) {
        this.baseDir = baseDir;
        this.displayName = displayName;
        this.fileLockManager = fileLockManager;
        this.gcFile = new File(baseDir, "gc.properties");
        this.locksDir = new File(baseDir, "locks");
        this.guard = ProducerGuard.adaptive();
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
            .add(locksDir)
            .build();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T useCache(String key, Supplier<? extends T> action) {
        String normalizedKey = normalizeCacheKey(key);
        return guard.guardByKey(normalizedKey, () -> {
            try (@SuppressWarnings("unused") FileLock lock = acquireLock(normalizedKey)) {
                return action.get();
            }
        });
    }

    @Override
    public void useCache(String key, Runnable action) {
        useCache(key, () -> {
            action.run();
            return null;
        });
    }

    private FileLock acquireLock(String normalizedKey) {
        FileLock lock = null;
        LockOptions lockOptions = DefaultLockOptions.mode(Exclusive);
        while (lock == null) {
            File lockFile = getLockFile(normalizedKey);
            // Gradle will never create and delete file.lock immediately, so we
            // save a few cycles on a first use case by not checking the validity of locks if it doesn't exist yet
            boolean shouldCheckLockValidity = lockFile.exists();
            lock = fileLockManager.lock(lockFile, lockOptions, displayName, "");
            if (shouldCheckLockValidity && !lock.isValid()) {
                lock.close();
                lock = null;
            }
        }
        return lock;
    }

    private File getLockFile(String key) {
        return new File(locksDir, key + ".lock");
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

    private static String normalizeCacheKey(String key) {
        String normalizedKey = FilenameUtils.separatorsToUnix(key);
        Preconditions.checkArgument(!normalizedKey.startsWith("/") && !normalizedKey.endsWith("/"), "Cache key path must be relative and not end with a slash: '%s'", key);
        return normalizedKey;
    }
}
