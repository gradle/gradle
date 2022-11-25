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
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DirectoryBuildCacheService implements LocalBuildCacheService, BuildCacheService {

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;
    private final FileAccessTracker fileAccessTracker;
    private final String failedFileSuffix;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DirectoryBuildCacheService(PathKeyFileStore fileStore, PersistentCache persistentCache, BuildCacheTempFileStore tempFileStore, FileAccessTracker fileAccessTracker, String failedFileSuffix) {
        this.fileStore = fileStore;
        this.persistentCache = persistentCache;
        this.tempFileStore = tempFileStore;
        this.fileAccessTracker = fileAccessTracker;
        this.failedFileSuffix = failedFileSuffix;
    }

    private static class LoadAction implements Action<File> {
        private final BuildCacheEntryReader reader;
        boolean loaded;

        private LoadAction(BuildCacheEntryReader reader) {
            this.reader = reader;
        }

        @Override
        public void execute(@Nonnull File file) {
            try {
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
        loadLocally(key, loadAction);
        return loadAction.loaded;
    }

    @Override
    public void loadLocally(final BuildCacheKey key, final Action<? super File> reader) {
        // We need to lock other processes out here because garbage collection can be under way in another process
        persistentCache.withFileLock(() -> {
            lock.readLock().lock();
            try {
                loadInsideLock(key, reader);
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    private void loadInsideLock(BuildCacheKey key, Action<? super File> reader) {
        LocallyAvailableResource resource = fileStore.get(key.getHashCode());
        if (resource == null) {
            return;
        }

        File file = resource.getFile();
        fileAccessTracker.markAccessed(file);

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

    @Override
    public void store(final BuildCacheKey key, final BuildCacheEntryWriter result) throws BuildCacheException {
        tempFileStore.withTempFile(key, new Action<File>() {
            @Override
            public void execute(@Nonnull File file) {
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

                storeLocally(key, file);
            }
        });
    }

    @Override
    public void storeLocally(final BuildCacheKey key, final File file) {
        persistentCache.withFileLock(new Runnable() {
            @Override
            public void run() {
                lock.writeLock().lock();
                try {
                    storeInsideLock(key, file);
                } finally {
                    lock.writeLock().unlock();
                }
            }
        });
    }

    private void storeInsideLock(BuildCacheKey key, File file) {
        LocallyAvailableResource resource = fileStore.move(key.getHashCode(), file);
        fileAccessTracker.markAccessed(resource.getFile());
    }

    @Override
    public void withTempFile(final BuildCacheKey key, final Action<? super File> action) {
        persistentCache.withFileLock(new Runnable() {
            @Override
            public void run() {
                tempFileStore.withTempFile(key, action);
            }
        });
    }

    @Override
    public void close() {
        persistentCache.close();
    }
}
