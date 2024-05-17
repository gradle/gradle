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
import org.gradle.api.NonNullApi;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoConsumer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@NonNullApi
public class DirectoryBuildCache implements BuildCacheTempFileStore, Closeable, LocalBuildCache {

    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;
    private final FileAccessTracker fileAccessTracker;
    private final String failedFileSuffix;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DirectoryBuildCache(PersistentCache persistentCache, FileAccessTracker fileAccessTracker, String failedFileSuffix) {
        this.persistentCache = persistentCache;
        // Create temporary files in the cache directory to ensure they are on the same file system,
        // and thus can always be moved into the cache proper atomically
        this.tempFileStore = new DefaultBuildCacheTempFileStore((prefix, suffix) -> {
            try {
                return Files.createTempFile(persistentCache.getBaseDir().toPath(), prefix, suffix).toFile();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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
            // Additional locking necessary because of https://github.com/gradle/gradle/issues/3537
            lock.readLock().lock();
            try {
                loadInsideLock(key, reader);
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    private void loadInsideLock(HashCode key, Consumer<? super File> reader) {
        File file = getCacheEntryFile(key);
        if (!file.exists()) {
            return;
        }

        fileAccessTracker.markAccessed(file);

        try {
            reader.accept(file);
        } catch (Exception e) {
            // Try to move the file out of the way in case its permanently corrupt
            // Don't delete, so that it can be potentially used for debugging
            File failedFile = new File(file.getAbsolutePath() + failedFileSuffix);
            FileUtils.deleteQuietly(failedFile);
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
        // We need to lock other processes out here because garbage collection can be under way in another process
        persistentCache.withFileLock(() -> {
            // Additional locking necessary because of https://github.com/gradle/gradle/issues/3537
            lock.writeLock().lock();
            try {
                storeInsideLock(key, file);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private void storeInsideLock(HashCode key, File sourceFile) {
        File targetFile = getCacheEntryFile(key);
        try {
            Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ignore) {
            // We already have the file in the build cache
            // Note that according to the documentation of `Files.move()`, whether this exception is thrown
            // is implementation specific: it can also happen that the target file gets overwritten, as if
            // `REPLACE_EXISTING` was specified. This seems to match the behavior exhibited by `File.renameTo()`.
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Couldn't move cache entry '%s' into local cache: %s", key, e), e);
        }
        fileAccessTracker.markAccessed(targetFile);
    }

    @Override
    public void withTempFile(HashCode key, Consumer<? super File> action) {
        persistentCache.withFileLock(() -> tempFileStore.withTempFile(key, action));
    }

    @Override
    public void close() {
        persistentCache.close();
    }

    private File getCacheEntryFile(HashCode key) {
        return new File(persistentCache.getBaseDir(), key.toString());
    }
}
