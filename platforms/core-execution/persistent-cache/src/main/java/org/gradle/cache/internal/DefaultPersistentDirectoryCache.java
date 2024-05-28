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

import org.apache.commons.io.FileUtils;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public class DefaultPersistentDirectoryCache extends DefaultPersistentDirectoryStore implements ReferencablePersistentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryCache.class);

    private final Properties properties = new Properties();
    private final Consumer<? super PersistentCache> initAction;

    public DefaultPersistentDirectoryCache(
        File dir,
        String displayName,
        Map<String, ?> properties,
        LockOptions lockOptions,
        Consumer<? super PersistentCache> initAction,
        CacheCleanupStrategy cacheCleanupStrategy,
        FileLockManager lockManager,
        ExecutorFactory executorFactory,
        BuildOperationRunner buildOperationRunner
    ) {
        super(dir, displayName, lockOptions, cacheCleanupStrategy, lockManager, executorFactory, buildOperationRunner);
        this.initAction = initAction;
        this.properties.putAll(properties);
    }

    @Override
    protected CacheInitializationAction getInitAction() {
        return new Initializer();
    }

    public Properties getProperties() {
        return properties;
    }

    private class Initializer implements CacheInitializationAction {
        @Override
        public boolean requiresInitialization(FileLock lock) {
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
                Properties cachedProperties = new Properties();
                try (InputStream propertiesInputStream = new FileInputStream(propertiesFile)) {
                    cachedProperties.load(propertiesInputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
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

        @Override
        public void initialize(FileLock fileLock) {
            try {
                File[] files = getBaseDir().listFiles();
                if (files == null) {
                    throw new IOException("Cannot list files in " + getBaseDir());
                }
                for (File file : files) {
                    if (fileLock.isLockFile(file) || file.equals(propertiesFile)) {
                        continue;
                    }
                    FileUtils.forceDelete(file);
                }
                initAction.accept(DefaultPersistentDirectoryCache.this);
                try (FileOutputStream propertiesFileOutputStream = new FileOutputStream(propertiesFile)) {
                    properties.store(propertiesFileOutputStream, null);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
