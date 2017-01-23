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

package org.gradle.api.internal.cache;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.state.FileDetails;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultFileContentCacheFactory implements FileContentCacheFactory, Closeable {
    private final ListenerManager listenerManager;
    private final FileHasher fileHasher;
    private final FileSystem fileSystem;
    private final FileSystemMirror fileSystemMirror;
    private final InMemoryTaskArtifactCache inMemoryTaskArtifactCache;
    private final PersistentCache cache;
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

    public DefaultFileContentCacheFactory(ListenerManager listenerManager, FileHasher fileHasher, FileSystem fileSystem, FileSystemMirror fileSystemMirror, CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache, Gradle gradle) {
        this.listenerManager = listenerManager;
        this.fileHasher = fileHasher;
        this.fileSystem = fileSystem;
        this.fileSystemMirror = fileSystemMirror;
        this.inMemoryTaskArtifactCache = inMemoryTaskArtifactCache;
        cache = cacheRepository
            .cache(gradle, "fileContent")
            .withDisplayName("file content cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, final Calculator<? extends V> calculator, Serializer<V> serializer) {
        PersistentIndexedCacheParameters<HashCode, V> parameters = new PersistentIndexedCacheParameters<HashCode, V>(name, hashCodeSerializer, serializer)
                .cacheDecorator(inMemoryTaskArtifactCache.decorator(normalizedCacheSize, true));
        PersistentIndexedCache<HashCode, V> store = cache.createCache(parameters);

        DefaultFileContentCache<V> cache = new DefaultFileContentCache<V>(name, fileHasher, fileSystem, fileSystemMirror, store, calculator);
        listenerManager.addListener(cache);
        return cache;
    }

    /**
     * Maintains 2 levels of in-memory caching. The first, fast, level indexes on file path and contains the value that is very likely to reflect the current contents of the file. This first cache is invalidated whenever any task actions are run.
     *
     * The second level indexes on the hash of file content and contains the value that was calculated from a file with the given hash.
     */
    private static class DefaultFileContentCache<V> implements FileContentCache<V>, TaskOutputsGenerationListener {
        private final Map<File, V> cache = new ConcurrentHashMap<File, V>();
        private final FileSystemMirror fileSystemMirror;
        private final PersistentIndexedCache<HashCode, V> contentCache;
        private final String name;
        private final FileHasher fileHasher;
        private final FileSystem fileSystem;
        private final Calculator<? extends V> calculator;

        DefaultFileContentCache(String name, FileHasher fileHasher, FileSystem fileSystem, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, V> contentCache, Calculator<? extends V> calculator) {
            this.name = name;
            this.fileHasher = fileHasher;
            this.fileSystem = fileSystem;
            this.fileSystemMirror = fileSystemMirror;
            this.contentCache = contentCache;
            this.calculator = calculator;
        }

        @Override
        public void beforeTaskOutputsGenerated() {
            // A very dumb strategy for invalidating cache
            cache.clear();
        }

        @Override
        public V get(File file) {
            // TODO - don't calculate the same value concurrently
            V value = cache.get(file);
            if (value == null) {
                FileType fileType;
                HashCode hashCode;
                FileDetails mirrorDetails = fileSystemMirror.getFile(file.getAbsolutePath());
                if (mirrorDetails != null) {
                    fileType = mirrorDetails.getType();
                    hashCode = mirrorDetails.getContent().getContentMd5();
                } else {
                    FileMetadataSnapshot fileDetails = fileSystem.stat(file);
                    fileType = fileDetails.getType();
                    if (fileType == FileType.RegularFile) {
                        hashCode = fileHasher.hash(file, fileDetails);
                    } else {
                        hashCode = null;
                    }
                }
                if (fileType == FileType.RegularFile) {
                    value = contentCache.get(hashCode);
                    if (value == null) {
                        value = calculator.calculate(file, fileType);
                        contentCache.put(hashCode, value);
                    }
                } else {
                    value = calculator.calculate(file, fileType);
                }
                cache.put(file, value);
            }
            return value;
        }
    }
}
