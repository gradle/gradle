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
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
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
 */
public class DefaultFineGrainedPersistentCache implements FineGrainedPersistentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFineGrainedPersistentCache.class);

    private static final LockOptions EXCLUSIVE_LOCKING_MODE = DefaultLockOptions.mode(Exclusive);

    private final ProducerGuard<String> guard;
    private final File locksDir;
    private final File internalDir;
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
        this.guard = ProducerGuard.adaptive();
        this.internalDir = new File(baseDir, ".internal");
        this.locksDir = new File(baseDir, LOCKS_DIR_RELATIVE_PATH);
        File gcFile = new File(internalDir, "gc.properties");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cleanupStrategy.getCleanupStrategy());
    }

    @Override
    public FineGrainedPersistentCache open() {
        try {
            alreadyCleaned.set(false);
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
        return ImmutableList.of(internalDir);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <T> T useCache(String key, Supplier<? extends T> action) {
        validateKey(key);
        return guard.guardByKey(key, () -> {
            try (@SuppressWarnings("unused") FileLock lock = acquireLock(key)) {
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

    private FileLock acquireLock(String key) {
        FileLock lock = null;
        while (lock == null) {
            File lockFile = getLockFile(key);
            lock = fileLockManager.lock(lockFile, EXCLUSIVE_LOCKING_MODE, displayName, "");
            // Verify the lock file hasn't been recreated between opening file handle and acquiring the lock.
            // Skip for new initializations to optimize the first use case. We assume that if we hold the lock,
            // and we are the first to access it, then no other process was able to delete/recreate the lock file.
            if (!lock.isFirstLockAccess() && !lock.isValid()) {
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

    private static void validateKey(String key) {
        Preconditions.checkArgument(!key.contains("/") && !key.contains("\\"), "Cache key path must not contain file separator: '%s'", key);
        Preconditions.checkArgument(!key.startsWith("."), "Cache key must not start with '.' character: '%s'", key);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
