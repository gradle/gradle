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

package org.gradle.cache.internal;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultFileContentCacheFactory implements FileContentCacheFactory, Closeable {
    private final ListenerManager listenerManager;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory;
    private final PersistentCache cache;
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();
    private final ConcurrentMap<String, DefaultFileContentCache<?>> caches = new ConcurrentHashMap<String, DefaultFileContentCache<?>>();

    public DefaultFileContentCacheFactory(ListenerManager listenerManager, FileSystemSnapshotter fileSystemSnapshotter, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, @Nullable Object scope) {
        this.listenerManager = listenerManager;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.inMemoryCacheDecoratorFactory = inMemoryCacheDecoratorFactory;
        cache = cacheRepository
            .cache(scope, "fileContent")
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
        PersistentIndexedCacheParameters<HashCode, V> parameters = PersistentIndexedCacheParameters.of(name, hashCodeSerializer, serializer)
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(normalizedCacheSize, true));
        PersistentIndexedCache<HashCode, V> store = cache.createCache(parameters);

        DefaultFileContentCache<V> cache = (DefaultFileContentCache<V>) caches.get(name);
        if (cache == null) {
            cache = new DefaultFileContentCache<V>(name, fileSystemSnapshotter, store, calculator);
            DefaultFileContentCache<V> existing = (DefaultFileContentCache<V>) caches.putIfAbsent(name, cache);
            if (existing == null) {
                listenerManager.addListener(cache);
            } else {
                cache = existing;
            }
        }

        cache.assertStoredIn(store);
        return cache;
    }

    /**
     * Maintains 2 levels of in-memory caching. The first, fast, level indexes on file path and contains the value that is very likely to reflect the current contents of the file. This first cache is invalidated whenever any task actions are run.
     *
     * The second level indexes on the hash of file content and contains the value that was calculated from a file with the given hash.
     */
    private static class DefaultFileContentCache<V> implements FileContentCache<V>, OutputChangeListener {
        private final Map<File, V> cache = new ConcurrentHashMap<File, V>();
        private final String name;
        private final FileSystemSnapshotter fileSystemSnapshotter;
        private final PersistentIndexedCache<HashCode, V> contentCache;
        private final Calculator<? extends V> calculator;

        DefaultFileContentCache(String name, FileSystemSnapshotter fileSystemSnapshotter, PersistentIndexedCache<HashCode, V> contentCache, Calculator<? extends V> calculator) {
            this.name = name;
            this.fileSystemSnapshotter = fileSystemSnapshotter;
            this.contentCache = contentCache;
            this.calculator = calculator;
        }

        @Override
        public void beforeOutputChange() {
            // A very dumb strategy for invalidating cache
            cache.clear();
        }

        @Override
        public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
            beforeOutputChange();
        }

        @Override
        public V get(File file) {
            // TODO - don't calculate the same value concurrently
            V value = cache.get(file);
            if (value == null) {
                HashCode contentHash = fileSystemSnapshotter.getRegularFileContentHash(file);
                if (contentHash != null) {
                    value = contentCache.get(contentHash);
                    if (value == null) {
                        value = calculator.calculate(file, true);
                        contentCache.put(contentHash, value);
                    }
                } else {
                    value = calculator.calculate(file, false);
                }
                cache.put(file, value);
            }
            return value;
        }

        private void assertStoredIn(PersistentIndexedCache<HashCode, V> store) {
            if (this.contentCache != store) {
                throw new IllegalStateException("Cache " + name + " cannot be recreated with different parameters");
            }
        }
    }
}
