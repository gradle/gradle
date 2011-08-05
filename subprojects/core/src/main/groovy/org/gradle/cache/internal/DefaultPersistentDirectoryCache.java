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
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.Properties;

import static org.gradle.cache.internal.CacheFactory.LockMode;

public class DefaultPersistentDirectoryCache implements PersistentCache {
    private final File dir;
    private final File propertiesFile;
    private final Properties properties = new Properties();
    private final File lockFile;

    public DefaultPersistentDirectoryCache(File dir, CacheUsage cacheUsage, Map<String, ?> properties, Action<? super PersistentCache> initAction) {
        this.dir = dir;
        propertiesFile = new File(dir, "cache.properties");
        lockFile = new File(dir, "cache.lock");
        this.properties.putAll(properties);
        init(cacheUsage, properties, initAction);
    }

    private void init(CacheUsage cacheUsage, Map<String, ?> properties, Action<? super PersistentCache> initAction) {
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }

        try {
            lockFile.createNewFile();
            RandomAccessFile lockFileAccess = new RandomAccessFile(lockFile, "rw");
            try {
                // Start with shared lock
                FileChannel lockChannel = lockFileAccess.getChannel();
                FileLock sharedLock = lock(lockChannel, LockMode.Shared);
                boolean valid;
                try {
                    valid = determineIfCacheIsValid(cacheUsage, properties);
                } finally {
                    sharedLock.release();
                }
                // TODO - handle case where another process rebuilds the cache in this window
                if (!valid) {
                    // Escalate to exclusive lock and initialise the cache
                    FileLock exclusiveLock = lock(lockChannel, LockMode.Exclusive);
                    try {
                        buildCacheDir(initAction);
                        // TODO - handle case where initializer fails
                        // TODO - handle case where this process crashes while initializing or writing properties file
                    } finally {
                        exclusiveLock.release();
                    }
                }
            } finally {
                lockFileAccess.close();
            }
            // TODO - need to keep hold of shared or exclusive lock until this cache is closed
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not open %s.", this), e);
        }
    }

    private FileLock lock(FileChannel lockChannel, LockMode lockMode) throws IOException, InterruptedException {
        long timeout = System.currentTimeMillis() + 20000;
        do {
            FileLock fileLock = lockChannel.tryLock(0, Long.MAX_VALUE, lockMode == LockMode.Shared);
            if (fileLock != null) {
                return fileLock;
            }
            Thread.sleep(200L);
        } while (System.currentTimeMillis() < timeout);
        throw new GradleException(String.format("Timeout waiting to acquire %s lock on %s. It is currently in use by another Gradle instance.", lockMode.toString().toLowerCase(), this));
    }

    @Override
    public String toString() {
        return String.format("Cache %s", dir);
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
