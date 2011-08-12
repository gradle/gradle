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

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.gradle.cache.internal.FileLockManager.LockMode;

public class DefaultPersistentDirectoryCache implements PersistentCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryCache.class);
    private final File dir;
    private final File propertiesFile;
    private final Properties properties = new Properties();
    private final FileLock lock;

    public DefaultPersistentDirectoryCache(File dir, CacheUsage cacheUsage, Map<String, ?> properties, LockMode lockMode, Action<? super PersistentCache> initAction, FileLockManager lockManager) {
        this.dir = dir;
        propertiesFile = new File(dir, "cache.properties");
        this.properties.putAll(properties);
        lock = init(cacheUsage, properties, initAction, lockMode, lockManager);
    }

    private FileLock init(CacheUsage cacheUsage, Map<String, ?> properties, final Action<? super PersistentCache> initAction, LockMode lockMode, FileLockManager lockManager) {
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        try {
            // Start with desired lock mode and check if cache is valid or not
            final FileLock lock = lockManager.lock(propertiesFile, lockMode, toString());
            try {
                boolean valid = determineIfCacheIsValid(cacheUsage, properties, lock);
                if (!valid) {
                    // Escalate to exclusive lock and rebuilt the cache
                    lock.writeToFile(new Runnable() {
                        public void run() {
                            buildCacheDir(initAction, lock);
                        }
                    });
                }
            } catch (Throwable throwable) {
                lock.close();
                throw throwable;
            }
            return lock;
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }
    }

    public void close() {
        lock.close();
    }

    @Override
    public String toString() {
        return String.format("cache directory %s", dir);
    }

    private void buildCacheDir(Action<? super PersistentCache> initAction, FileLock fileLock) {
        for (File file : dir.listFiles()) {
            if (fileLock.isLockFile(file) || file.equals(propertiesFile)) {
                continue;
            }
            GFileUtils.forceDelete(file);
        }
        if (initAction != null) {
            initAction.execute(this);
        }
        GUtil.saveProperties(properties, propertiesFile);
    }

    private boolean determineIfCacheIsValid(CacheUsage cacheUsage, Map<String, ?> properties, FileLock lock) throws IOException {
        if (cacheUsage != CacheUsage.ON) {
            LOGGER.debug("Invalidating {} as cache usage is set to rebuild.", this);
            return false;
        }
        if (!lock.getUnlockedCleanly()) {
            LOGGER.debug("Invalidating {} as it was not close cleanly.", this);
            return false;
        }
        Properties currentProperties = GUtil.loadProperties(propertiesFile);
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (!entry.getValue().toString().equals(currentProperties.getProperty(entry.getKey()))) {
                LOGGER.debug("Invalidating {} as cache property {} has changed value.", this, entry.getKey());
                return false;
            }
        }
        return true;
    }

    public Properties getProperties() {
        return properties;
    }

    public File getBaseDir() {
        return dir;
    }

    public FileLock getLock() {
        return lock;
    }
}
