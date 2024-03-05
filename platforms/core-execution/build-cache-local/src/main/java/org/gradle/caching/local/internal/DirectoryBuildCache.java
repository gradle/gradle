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
import org.gradle.util.internal.GFileUtils;

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
import java.util.function.Consumer;

@NonNullApi
public class DirectoryBuildCache implements BuildCacheTempFileStore, Closeable, LocalBuildCache {

    private final PersistentCache persistentCache;
    private final BuildCacheTempFileStore tempFileStore;
    private final FileAccessTracker fileAccessTracker;
    private final String failedFileSuffix;

    public DirectoryBuildCache(PersistentCache persistentCache, BuildCacheTempFileStore tempFileStore, FileAccessTracker fileAccessTracker, String failedFileSuffix) {
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
        persistentCache.withFileLock(() -> loadInsideLock(key, reader));
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
        persistentCache.withFileLock(() -> storeInsideLock(key, file));
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
            throw new UncheckedIOException("Couldn't move cache entry into local cache: " + key, e);
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
