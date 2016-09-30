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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Properties;

public class DefaultPersistentDirectoryCache extends DefaultPersistentDirectoryStore implements ReferencablePersistentCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryCache.class);
    private final Properties properties = new Properties();
    private final Action<? super PersistentCache> initAction;
    private final CacheValidator validator;
    private boolean didRebuild;

    public DefaultPersistentDirectoryCache(File dir, String displayName, CacheValidator validator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initAction, FileLockManager lockManager, ExecutorFactory executorFactory) {
        super(dir, displayName, lockTarget, lockOptions, lockManager, executorFactory);
        this.validator = validator;
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
        public boolean requiresInitialization(FileLock lock) {
            if (!didRebuild) {
                if (validator!=null && !validator.isValid()) {
                    LOGGER.debug("Invalidating {} as cache validator return false.", DefaultPersistentDirectoryCache.this);
                    return true;
                }
            }

            if (!lock.getUnlockedCleanly()) {
                if (!lock.getState().isInInitialState()) {
                    LOGGER.warn("Invalidating {} as it was not closed cleanly.", DefaultPersistentDirectoryCache.this);
                }
                return true;
            }

            Properties cachedProperties = GUtil.loadProperties(propertiesFile);
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String previousValue = cachedProperties.getProperty(entry.getKey().toString());
                String currentValue = entry.getValue().toString();
                if (!previousValue.equals(currentValue)) {
                    LOGGER.debug("Invalidating {} as cache property {} has changed from {} to {}.", DefaultPersistentDirectoryCache.this, entry.getKey(), previousValue, currentValue);
                    return true;
                }
            }
            return false;
        }

        public void initialize(FileLock fileLock) {
            for (File file : getBaseDir().listFiles()) {
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
}
