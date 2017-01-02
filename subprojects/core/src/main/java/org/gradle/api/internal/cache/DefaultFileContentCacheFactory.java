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
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultFileContentCacheFactory implements FileContentCacheFactory {
    private final ListenerManager listenerManager;
    private final FileContentCacheBackingStore store;
    private final FileHasher fileHasher;
    private final FileSystem fileSystem;

    public DefaultFileContentCacheFactory(ListenerManager listenerManager, FileContentCacheBackingStore store, FileHasher fileHasher, FileSystem fileSystem) {
        this.listenerManager = listenerManager;
        this.store = store;
        this.fileHasher = fileHasher;
        this.fileSystem = fileSystem;
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, final Calculator<? extends V> calculator) {
        com.google.common.cache.Cache<HashCode, V> contentCache = store.getStore(name, normalizedCacheSize);
        DefaultFileContentCache<V> cache = new DefaultFileContentCache<V>(fileHasher, fileSystem, contentCache, calculator);
        listenerManager.addListener(cache);
        return cache;
    }

    private static class DefaultFileContentCache<V> implements FileContentCache<V>, TaskOutputsGenerationListener {
        private final Map<File, V> cache = new ConcurrentHashMap<File, V>();
        private final com.google.common.cache.Cache<HashCode, V> contentCache;
        private final FileHasher fileHasher;
        private final FileSystem fileSystem;
        private final Calculator<? extends V> calculator;

        public DefaultFileContentCache(FileHasher fileHasher, FileSystem fileSystem, com.google.common.cache.Cache<HashCode, V> contentCache, Calculator<? extends V> calculator) {
            this.fileHasher = fileHasher;
            this.fileSystem = fileSystem;
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
                FileMetadataSnapshot fileDetails = fileSystem.stat(file);
                if (fileDetails.getType() == FileType.RegularFile) {
                    HashCode hash = fileHasher.hash(file, fileDetails);
                    value = contentCache.getIfPresent(hash);
                    if (value == null) {
                        value = calculator.calculate(file, fileDetails);
                        contentCache.put(hash, value);
                    }
                } else {
                    value = calculator.calculate(file, fileDetails);
                }
                cache.put(file, value);
            }
            return value;
        }
    }
}
