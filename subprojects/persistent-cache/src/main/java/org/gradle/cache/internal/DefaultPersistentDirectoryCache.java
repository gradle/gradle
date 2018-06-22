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

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class DefaultPersistentDirectoryCache extends DefaultPersistentDirectoryStore implements ReferencablePersistentCache {
    public static final int CLEANUP_INTERVAL_IN_HOURS = 24;

    // Cleanup is performed while the file lock is being held. Using a timeout
    // well below the limit used by another process to wait for the lock avoids
    // those from timing out while waiting to acquire the file lock. Cleanup is
    // usually much faster than this timeout.
    private static final int DEFAULT_CLEANUP_TIMEOUT = DefaultFileLockManager.DEFAULT_LOCK_TIMEOUT / 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryCache.class);
    private final Properties properties = new Properties();
    private final Action<? super PersistentCache> initAction;
    private final CleanupAction cleanupAction;
    private final CacheValidator validator;
    private boolean didRebuild;

    public DefaultPersistentDirectoryCache(File dir, String displayName, CacheValidator validator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initAction, CleanupAction cleanupAction, FileLockManager lockManager, ExecutorFactory executorFactory) {
        super(dir, displayName, lockTarget, lockOptions, lockManager, executorFactory);
        this.validator = validator;
        this.initAction = initAction;
        this.cleanupAction = cleanupAction;
        this.properties.putAll(properties);
    }

    @Override
    protected CacheInitializationAction getInitAction() {
        return new Initializer();
    }

    @Override
    public CacheCleanupAction getCleanupAction() {
        return new Cleanup();
    }

    public Properties getProperties() {
        return properties;
    }

    private class Initializer implements CacheInitializationAction {
        public boolean requiresInitialization(FileLock lock) {
            if (!didRebuild) {
                if (validator!=null && !validator.isValid()) {
                    LOGGER.debug("Invalidating {} as cache validator return false.", DefaultPersistentDirectoryCache.this);
                    return true;
                }
            }

            if (!lock.getUnlockedCleanly()) {
                if (lock.getState().canDetectChanges() && !lock.getState().isInInitialState()) {
                    LOGGER.warn("Invalidating {} as it was not closed cleanly.", DefaultPersistentDirectoryCache.this);
                }
                return true;
            }

            if (!properties.isEmpty()) {
                if (!propertiesFile.exists()) {
                    LOGGER.debug("Invalidating {} as cache properties file {} is missing and cache properties are not empty.", DefaultPersistentDirectoryCache.this, propertiesFile.getAbsolutePath());
                    return true;
                }
                Properties cachedProperties = GUtil.loadProperties(propertiesFile);
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String previousValue = cachedProperties.getProperty(entry.getKey().toString());
                    String currentValue = entry.getValue().toString();
                    if (!currentValue.equals(previousValue)) {
                        LOGGER.debug("Invalidating {} as cache property {} has changed from {} to {}.", DefaultPersistentDirectoryCache.this, entry.getKey(), previousValue, currentValue);
                        return true;
                    }
                }
            }
            return false;
        }

        public void initialize(FileLock fileLock) {
            File[] files = getBaseDir().listFiles();
            if (files == null) {
                throw new UncheckedIOException("Cannot list files in " + getBaseDir());
            }
            for (File file : files) {
                if (fileLock.isLockFile(file) || file.equals(propertiesFile)) {
                    continue;
                }
                GFileUtils.forceDelete(file);
            }
            if (initAction != null) {
                initAction.execute(DefaultPersistentDirectoryCache.this);
            }
            GUtil.saveProperties(properties, propertiesFile);
            didRebuild = true;
        }
    }

    private class Cleanup implements CacheCleanupAction {
        @Override
        public boolean requiresCleanup() {
            if (cleanupAction != null) {
                if (!gcFile.exists()) {
                    GFileUtils.touch(gcFile);
                } else {
                    long duration = System.currentTimeMillis() - gcFile.lastModified();
                    long timeInHours = TimeUnit.MILLISECONDS.toHours(duration);
                    LOGGER.debug("{} has last been fully cleaned up {} hours ago", DefaultPersistentDirectoryCache.this, timeInHours);
                    return timeInHours >= CLEANUP_INTERVAL_IN_HOURS;
                }
            }
            return false;
        }

        @Override
        public void cleanup() {
            if (cleanupAction != null) {
                CountdownTimer timer = Time.startCountdownTimer(DEFAULT_CLEANUP_TIMEOUT);
                cleanupAction.clean(DefaultPersistentDirectoryCache.this, timer);
                if (!timer.hasExpired()) {
                    GFileUtils.touch(gcFile);
                }
                LOGGER.info("{} cleaned up in {}.", DefaultPersistentDirectoryCache.this, timer.getElapsed());
            }
        }
    }
}
