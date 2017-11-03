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
import org.gradle.cache.PersistentCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DirectoryBuildCacheService implements LocalBuildCacheService, BuildCacheService {

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;
    private final String failedFileSuffix;

    public DirectoryBuildCacheService(PathKeyFileStore fileStore, PersistentCache persistentCache, BuildCacheTempFileStore tempFileStore, String failedFileSuffix) {
        this.fileStore = fileStore;
        this.persistentCache = persistentCache;
        this.tempFileStore = tempFileStore;
        this.failedFileSuffix = failedFileSuffix;
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
        persistentCache.withFileLock(new Runnable() {
            @Override
            public void run() {
                LocallyAvailableResource resource = fileStore.get(key.getHashCode());
                if (resource != null) {
                    final File file = resource.getFile();
                    GFileUtils.touch(file); // Mark as recently used

                    try {
                        reader.execute(file);
                    } catch (Exception e) {
                        // Try to move the file out of the way in case its permanently corrupt
                        // Don't delete, so that it can be potentially used for debugging
                        File failedFile = new File(file.getAbsolutePath() + failedFileSuffix);
                        GFileUtils.deleteQuietly(failedFile);
                        //noinspection ResultOfMethodCallIgnored
                        file.renameTo(failedFile);

                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
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
        persistentCache.withFileLock(new Runnable() {
            @Override
            public void run() {
                tempFileStore.allocateTempFile(key, action);
            }
        });
    }

    @Override
    public void close() {
        persistentCache.close();
    }
}
