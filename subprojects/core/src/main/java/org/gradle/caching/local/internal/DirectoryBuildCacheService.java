/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local.internal;

import com.google.common.io.Closer;
import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FixedSizeOldestCacheCleanup;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.gradle.cache.internal.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DirectoryBuildCacheService implements BuildCacheService {
    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;

    public DirectoryBuildCacheService(CacheRepository cacheRepository, BuildOperationExecutor buildOperationExecutor, File baseDir, long targetCacheSize) {
        this.fileStore = new PathKeyFileStore(baseDir);
        this.persistentCache = cacheRepository
            .cache(checkDirectory(baseDir))
            .withCleanup(new FixedSizeOldestCacheCleanup(buildOperationExecutor, targetCacheSize))
            .withDisplayName("Build cache")
            .withLockOptions(mode(None))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
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
        // We need to lock here because garbage collection can be under way in another process
        return persistentCache.withFileLock(new Factory<Boolean>() {
            @Override
            public Boolean create() {
                LocallyAvailableResource resource = fileStore.get(key.getHashCode());
                if (resource == null) {
                    return false;
                }

                try {
                    // Mark as recently used
                    GFileUtils.touch(resource.getFile());

                    Closer closer = Closer.create();
                    FileInputStream stream = closer.register(new FileInputStream(resource.getFile()));
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
        });
    }

    @Override
    public void store(final BuildCacheKey key, final BuildCacheEntryWriter result) throws BuildCacheException {
        final String hashCode = key.getHashCode();
        final File tempFile;
        try {
            tempFile = File.createTempFile(hashCode, ".part", persistentCache.getBaseDir());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        try {
            try {
                Closer closer = Closer.create();
                OutputStream output = closer.register(new FileOutputStream(tempFile));
                try {
                    result.writeTo(output);
                } finally {
                    closer.close();
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            persistentCache.useCache(new Runnable() {
                @Override
                public void run() {
                    fileStore.move(hashCode, tempFile);
                }
            });
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
    }

    @Override
    public void close() throws IOException {
        persistentCache.close();
    }
}
