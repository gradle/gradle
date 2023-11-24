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
import org.gradle.api.NonNullApi;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoConsumer;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.util.internal.GFileUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@NonNullApi
public class DirectoryBuildCache implements BuildCacheTempFileStore, Closeable, LocalBuildCache {

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;
    private final FileAccessTracker fileAccessTracker;
    private final String failedFileSuffix;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DirectoryBuildCache(PathKeyFileStore fileStore, PersistentCache persistentCache, BuildCacheTempFileStore tempFileStore, FileAccessTracker fileAccessTracker, String failedFileSuffix) {
        this.fileStore = fileStore;
        this.persistentCache = persistentCache;
        this.tempFileStore = tempFileStore;
        this.fileAccessTracker = fileAccessTracker;
        this.failedFileSuffix = failedFileSuffix;
    }

    @Override
    public boolean load(HashCode key, IoConsumer<InputStream> reader) {
        AtomicBoolean loaded = new AtomicBoolean(false);
        loadLocally(key, file -> {
            try {
                Closer closer = Closer.create();
                FileInputStream stream = closer.register(new FileInputStream(file));
                try {
                    reader.accept(stream);
                    loaded.set(true);
                } finally {
                    closer.close();
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
        return loaded.get();
    }

    @Override
    public void loadLocally(HashCode key, Consumer<? super File> reader) {
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

    private void loadInsideLock(HashCode key, Consumer<? super File> reader) {
        LocallyAvailableResource resource = fileStore.get(key.toString());
        if (resource == null) {
            return;
        }

        File file = resource.getFile();
        fileAccessTracker.markAccessed(file);

        try {
            reader.accept(file);
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
    public void store(HashCode key, IoConsumer<OutputStream> result) {
        tempFileStore.withTempFile(key, file -> {
            try {
                Closer closer = Closer.create();
                try {
                    result.accept(closer.register(new FileOutputStream(file)));
                } catch (Exception e) {
                    throw closer.rethrow(e);
                } finally {
                    closer.close();
                }
            } catch (IOException ex) {
                throw UncheckedException.throwAsUncheckedException(ex);
            }

            storeLocally(key, file);
        });
    }

    @Override
    public void storeLocally(HashCode key, File file) {
        persistentCache.withFileLock(() -> {
            lock.writeLock().lock();
            try {
                storeInsideLock(key, file);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private void storeInsideLock(HashCode key, File file) {
        LocallyAvailableResource resource = fileStore.move(key.toString(), file);
        fileAccessTracker.markAccessed(resource.getFile());
    }

    @Override
    public void withTempFile(HashCode key, Consumer<? super File> action) {
        persistentCache.withFileLock(() -> tempFileStore.withTempFile(key, action));
    }

    @Override
    public void close() {
        persistentCache.close();
    }
}
