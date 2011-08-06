/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.Properties;

import static org.gradle.cache.internal.CacheFactory.LockMode;

public class DefaultPersistentDirectoryCache implements PersistentCache {
    private static final int LOCK_TIMEOUT = 15000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPersistentDirectoryCache.class);
    private final File dir;
    private final File propertiesFile;
    private final Properties properties = new Properties();
    private final File lockFile;
    private FileLock fileLock;
    private RandomAccessFile lockFileAccess;

    public DefaultPersistentDirectoryCache(File dir, CacheUsage cacheUsage, Map<String, ?> properties, LockMode lockMode, Action<? super PersistentCache> initAction) {
        this.dir = dir;
        propertiesFile = new File(dir, "cache.properties");
        lockFile = new File(dir, "cache.lock");
        this.properties.putAll(properties);
        init(cacheUsage, properties, initAction, lockMode);
    }

    private void init(CacheUsage cacheUsage, Map<String, ?> properties, Action<? super PersistentCache> initAction, LockMode lockMode) {
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }

        try {
            lockFile.createNewFile();
            lockFileAccess = new RandomAccessFile(lockFile, "rw");

            try {
                // Start with desired lock mode and check if cache is valid or not
                FileChannel lockChannel = lockFileAccess.getChannel();
                FileLock validateLock = lock(lockChannel, lockMode);
                boolean valid = determineIfCacheIsValid(cacheUsage, properties);

                if (valid) {
                    this.fileLock = validateLock;
                    return;
                }

                // Escalate to exclusive lock and initialise the cache
                FileLock initialiseLock;
                if (lockMode == LockMode.Exclusive) {
                    initialiseLock = validateLock;
                } else {
                    LOGGER.debug("Releasing lock on {}.", this);
                    validateLock.release();
                    // TODO - handle case where another process rebuilds the cache in this window
                    initialiseLock = lock(lockChannel, LockMode.Exclusive);
                }

                buildCacheDir(initAction);
                // TODO - handle case where initializer fails
                // TODO - handle case where this process crashes while initializing or writing properties file

                if (lockMode == LockMode.Exclusive) {
                    this.fileLock = initialiseLock;
                } else {
                    LOGGER.debug("Releasing lock on {}.", this);
                    initialiseLock.release();
                    // TODO - handle case where another process rebuilds the cache in this window
                    this.fileLock = lock(lockChannel, lockMode);
                }
            } catch (Throwable throwable) {
                // This also releases the locks, if any
                lockFileAccess.close();
                throw throwable;
            }
        } catch (CacheOpenException e) {
            throw e;
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }
    }

    private FileLock lock(FileChannel lockChannel, LockMode lockMode) throws IOException, InterruptedException {
        LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode, this);
        long timeout = System.currentTimeMillis() + LOCK_TIMEOUT;
        do {
            FileLock fileLock = lockChannel.tryLock(0, Long.MAX_VALUE, lockMode == LockMode.Shared);
            if (fileLock != null) {
                LOGGER.debug("Lock acquired.");
                return fileLock;
            }
            Thread.sleep(200L);
        } while (System.currentTimeMillis() < timeout);
        throw new CacheOpenException(String.format("Timeout waiting to lock %s. It is currently in use by another Gradle instance.", this));
    }

    public void close() {
        try {
            if (fileLock != null) {
                LOGGER.debug("Releasing lock on {}.", this);
                fileLock.release();
            }
            if (lockFileAccess != null) {
                lockFileAccess.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return String.format("cache directory %s", dir);
    }

    private void buildCacheDir(Action<? super PersistentCache> initAction) throws IOException {
        for (File file : dir.listFiles()) {
            if (file.equals(lockFile) || file.equals(propertiesFile)) {
                continue;
            }
            FileUtils.forceDelete(file);
        }
        if (initAction != null) {
            initAction.execute(this);
        }
        GUtil.saveProperties(properties, propertiesFile);
    }

    private boolean determineIfCacheIsValid(CacheUsage cacheUsage, Map<String, ?> properties) {
        if (cacheUsage != CacheUsage.ON) {
            return false;
        }

        if (!propertiesFile.isFile()) {
            return false;
        }

        Properties currentProperties = GUtil.loadProperties(propertiesFile);
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (!entry.getValue().toString().equals(currentProperties.getProperty(entry.getKey()))) {
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
}
