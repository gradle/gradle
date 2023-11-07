/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.LockOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.internal.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.gradle.cache.internal.CacheInitializationAction.NO_INIT_REQUIRED;

public class DefaultPersistentDirectoryStore implements ReferencablePersistentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryStore.class);

    public static final int CLEANUP_INTERVAL_IN_HOURS = 24;

    private final File cacheDir;
    private final File lockDir;
    private final CacheBuilder.LockTarget lockTarget;
    private final LockOptions lockOptions;
    @Nullable
    private final CacheCleanupStrategy cacheCleanupStrategy;
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final String displayName;
    protected final File propertiesFile;
    private final File gcFile;
    private final ProgressLoggerFactory progressLoggerFactory;
    private DefaultCacheCoordinator cacheAccess;

    public DefaultPersistentDirectoryStore(
        File cacheDir,
        File lockDir,
        @Nullable String displayName,
        CacheBuilder.LockTarget lockTarget,
        LockOptions lockOptions,
        @Nullable CacheCleanupStrategy cacheCleanupStrategy,
        FileLockManager fileLockManager,
        ExecutorFactory executorFactory,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        this.cacheDir = cacheDir;
        this.lockDir = lockDir;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.cacheCleanupStrategy = cacheCleanupStrategy;
        this.lockManager = fileLockManager;
        this.executorFactory = executorFactory;
        this.propertiesFile = new File(cacheDir, "cache.properties");
        this.gcFile = new File(cacheDir, "gc.properties");
        this.progressLoggerFactory = progressLoggerFactory;
        this.displayName = displayName != null ? (displayName + " (" + cacheDir + ")") : ("cache directory " + cacheDir.getName() + " (" + cacheDir + ")");
    }

    @Override
    public DefaultPersistentDirectoryStore open() {
        GFileUtils.mkdirs(cacheDir);
        GFileUtils.mkdirs(lockDir);
        cacheAccess = createCacheAccess();
        try {
            cacheAccess.open();
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }

        return this;
    }

    private DefaultCacheCoordinator createCacheAccess() {
        return new DefaultCacheCoordinator(displayName, getLockTarget(lockOptions), lockOptions, cacheDir, lockManager, getInitAction(), getCleanupExecutor(), executorFactory);
    }

    private File getLockTarget(LockOptions lockOptions) {
        switch (lockTarget) {
            case CacheDirectory:
            case DefaultTarget:
                return lockOptions.getLockDir() == null ? cacheDir : lockOptions.getLockDir();
            case CachePropertiesFile:
                return propertiesFile;
            default:
                throw new IllegalArgumentException("Unsupported lock target: " + lockTarget);
        }
    }

    protected CacheInitializationAction getInitAction() {
        return NO_INIT_REQUIRED;
    }

    protected CacheCleanupExecutor getCleanupExecutor() {
        return new CleanupExecutor();
    }

    @Override
    public void close() {
        if (cacheAccess != null) {
            try {
                cacheAccess.close();
            } finally {
                cacheAccess = null;
            }
        }
    }

    @Override
    public File getBaseDir() {
        return cacheDir;
    }

    @Override
    public Collection<File> getReservedCacheFiles() {
        return Arrays.asList(propertiesFile, gcFile, determineLockTargetFile(getLockTarget(lockOptions)));
    }

    // TODO: move all this to LockOptions
    // TODO: Duplicated in DefaultFileLockManager
    static File determineLockTargetFile(File target) {
        if (target.isDirectory()) {
            return new File(target, target.getName() + ".lock");
        } else {
            return new File(target.getParentFile(), target.getName() + ".lock");
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <K, V> IndexedCache<K, V> createIndexedCache(IndexedCacheParameters<K, V> parameters) {
        return cacheAccess.newCache(parameters);
    }

    @Override
    public <K, V> IndexedCache<K, V> createIndexedCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
        return cacheAccess.newCache(IndexedCacheParameters.of(name, keyType, valueSerializer));
    }

    @Override
    public <K, V> boolean indexedCacheExists(IndexedCacheParameters<K, V> parameters) {
        return cacheAccess.cacheExists(parameters);
    }

    @Override
    public <T> T withFileLock(Factory<? extends T> action) {
        return cacheAccess.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cacheAccess.withFileLock(action);
    }

    @Override
    public <T> T useCache(Factory<? extends T> action) {
        return cacheAccess.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cacheAccess.useCache(action);
    }

    @Override
    public void cleanup() {
        cacheAccess.cleanup();
    }

    private class CleanupExecutor implements CacheCleanupExecutor {
        private boolean requiresCleanup() {
            if (cacheDir.exists() && cacheCleanupStrategy != null) {
                if (!gcFile.exists()) {
                    GFileUtils.touch(gcFile);
                } else {
                    long duration = System.currentTimeMillis() - gcFile.lastModified();
                    long timeInHours = TimeUnit.MILLISECONDS.toHours(duration);
                    LOGGER.debug("{} has last been fully cleaned up {} hours ago", DefaultPersistentDirectoryStore.this, timeInHours);
                    return cacheCleanupStrategy.getCleanupFrequency().requiresCleanup(gcFile.lastModified());
                }
            }
            return false;
        }

        @Override
        public void cleanup() {
            if (cacheCleanupStrategy != null && requiresCleanup()) {
                String description = "Cleaning " + getDisplayName();
                ProgressLogger progressLogger = progressLoggerFactory.newOperation(CacheCleanupExecutor.class).start(description, description);
                Timer timer = Time.startTimer();
                try {
                    cacheCleanupStrategy.getCleanupAction().clean(DefaultPersistentDirectoryStore.this, new DefaultCleanupProgressMonitor(progressLogger));
                    GFileUtils.touch(gcFile);
                } finally {
                    LOGGER.info("{} cleaned up in {}.", DefaultPersistentDirectoryStore.this, timer.getElapsed());
                    progressLogger.completed();
                }
            }
        }
    }

}
