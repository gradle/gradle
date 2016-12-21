/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal;

import com.google.common.io.Closer;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.caching.BuildCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.Factory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.gradle.cache.internal.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class LocalDirectoryBuildCache implements BuildCache {
    private final PersistentCache persistentCache;

    public LocalDirectoryBuildCache(CacheRepository cacheRepository, File directory) {
        this(cacheRepository.cache(checkDirectory(directory)));
    }

    public LocalDirectoryBuildCache(CacheRepository cacheRepository, String cacheKey) {
        this(cacheRepository.cache(cacheKey));
    }

    private LocalDirectoryBuildCache(CacheBuilder cacheBuilder) {
        this.persistentCache = cacheBuilder
            .withDisplayName("Build cache")
            .withLockOptions(mode(None))
            .open();
    }

    private static File checkDirectory(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be a directory", directory));
            }
            if (!directory.canRead()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be readable", directory));
            }
            if (!directory.canWrite()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be writable", directory));
            }
        } else {
            if (!directory.mkdirs()) {
                throw new UncheckedIOException(String.format("Could not create cache directory: %s", directory));
            }
        }
        return directory;
    }

    @Override
    public boolean load(final BuildCacheKey key, final BuildCacheEntryReader reader) throws BuildCacheException {
        return persistentCache.useCache("load build cache entry", new Factory<Boolean>() {
            @Override
            public Boolean create() {
                File file = getFile(key.getHashCode());
                if (file.isFile()) {
                    try {
                        Closer closer = Closer.create();
                        FileInputStream stream = closer.register(new FileInputStream(file));
                        try {
                            reader.readFrom(stream);
                            return true;
                        } finally {
                            closer.close();
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void store(final BuildCacheKey key, final BuildCacheEntryWriter result) throws BuildCacheException {
        persistentCache.useCache("store build cache entry", new Runnable() {
            @Override
            public void run() {
                File file = getFile(key.getHashCode());
                try {
                    Closer closer = Closer.create();
                    OutputStream output = closer.register(new FileOutputStream(file));
                    try {
                        result.writeTo(output);
                    } finally {
                        closer.close();
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        });
    }

    private File getFile(String key) {
        return new File(persistentCache.getBaseDir(), key);
    }

    @Override
    public String getDescription() {
        return "a local build cache (" + persistentCache.getBaseDir() + ")";
    }

    @Override
    public void close() throws IOException {
        persistentCache.close();
    }
}
