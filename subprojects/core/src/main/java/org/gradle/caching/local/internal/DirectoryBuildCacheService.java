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
import org.gradle.api.Action;
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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.gradle.cache.internal.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DirectoryBuildCacheService implements LocalBuildCacheService, BuildCacheService {

    public static final String FAILED_READ_SUFFIX = ".failed";

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;

    public DirectoryBuildCacheService(CacheRepository cacheRepository, BuildOperationExecutor buildOperationExecutor, File baseDir, long targetCacheSize) {
        this.fileStore = new PathKeyFileStore(baseDir);
        this.persistentCache = cacheRepository
            .cache(checkDirectory(baseDir))
            .withCleanup(new FixedSizeOldestCacheCleanup(buildOperationExecutor, targetCacheSize))
            .withDisplayName("Build cache")
            .withLockOptions(mode(None))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .open();

        tempFileStore = new DefaultBuildCacheTempFileStore(baseDir);
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

    private static class LoadAction implements Action<File> {
        private final BuildCacheEntryReader reader;
        boolean loaded;

        private LoadAction(BuildCacheEntryReader reader) {
            this.reader = reader;
        }

        @Override
        public void execute(File file) {
            try {
                // Mark as recently used
                GFileUtils.touch(file);

                Closer closer = Closer.create();
                FileInputStream stream = closer.register(new FileInputStream(file));
                try {
                    reader.readFrom(stream);
                    loaded = true;
                } finally {
                    closer.close();
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    @Override
    public boolean load(final BuildCacheKey key, final BuildCacheEntryReader reader) throws BuildCacheException {
        LoadAction loadAction = new LoadAction(reader);
        load(key, loadAction);
        return loadAction.loaded;
    }

    @Override
    public void load(final BuildCacheKey key, final Action<? super File> reader) {
        // We need to lock here because garbage collection can be under way in another process
        persistentCache.withFileLock(new Factory<Void>() {
            @Override
            public Void create() {
                LocallyAvailableResource resource = fileStore.get(key.getHashCode());
                if (resource != null) {
                    final File file = resource.getFile();
                    GFileUtils.touch(file); // Mark as recently used

                    try {
                        reader.execute(file);
                    } catch (Exception e) {
                        // Try to move the file out of the way in case its permanently corrupt
                        // Don't delete, so that it can be potentially used for debugging
                        File failedFile = new File(file.getAbsolutePath() + FAILED_READ_SUFFIX);
                        GFileUtils.deleteQuietly(failedFile);
                        //noinspection ResultOfMethodCallIgnored
                        file.renameTo(failedFile);

                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void store(final BuildCacheKey key, final BuildCacheEntryWriter result) throws BuildCacheException {
        tempFileStore.allocateTempFile(key, new Action<File>() {
            @Override
            public void execute(final File file) {
                try {
                    Closer closer = Closer.create();
                    try {
                        result.writeTo(closer.register(new FileOutputStream(file)));
                    } catch (Exception e) {
                        throw closer.rethrow(e);
                    } finally {
                        closer.close();
                    }
                } catch (IOException ex) {
                    throw UncheckedException.throwAsUncheckedException(ex);
                }

                store(key, file);
            }
        });
    }

    @Override
    public void store(final BuildCacheKey key, final File file) {
        persistentCache.useCache(new Runnable() {
            @Override
            public void run() {
                fileStore.move(key.getHashCode(), file);
            }
        });
    }

    @Override
    public void allocateTempFile(final BuildCacheKey key, final Action<? super File> action) {
        tempFileStore.allocateTempFile(key, action);
    }

    @Override
    public void close() {
        persistentCache.close();
    }
}
