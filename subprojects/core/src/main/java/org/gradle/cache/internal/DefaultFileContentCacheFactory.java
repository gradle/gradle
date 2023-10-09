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

import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultFileContentCacheFactory implements FileContentCacheFactory, Closeable {
    private final ListenerManager listenerManager;
    private final FileSystemAccess fileSystemAccess;
    private final InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory;
    private final PersistentCache cache;
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();
    private final ConcurrentMap<String, DefaultFileContentCache<?>> caches = new ConcurrentHashMap<>();

    public DefaultFileContentCacheFactory(ListenerManager listenerManager, FileSystemAccess fileSystemAccess, ScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.listenerManager = listenerManager;
        this.fileSystemAccess = fileSystemAccess;
        this.inMemoryCacheDecoratorFactory = inMemoryCacheDecoratorFactory;
        cache = cacheBuilderFactory
            .createCacheBuilder("fileContent")
            .withDisplayName("file content cache")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemandExclusive)) // Lock on demand
            .open();
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, final Calculator<? extends V> calculator, Serializer<V> serializer) {
        IndexedCacheParameters<HashCode, V> parameters = IndexedCacheParameters.of(name, hashCodeSerializer, serializer)
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(normalizedCacheSize, true));
        IndexedCache<HashCode, V> store = cache.createIndexedCache(parameters);

        DefaultFileContentCache<V> cache = Cast.uncheckedCast(caches.get(name));
        if (cache == null) {
            cache = new DefaultFileContentCache<>(name, fileSystemAccess, store, calculator);
            DefaultFileContentCache<V> existing = Cast.uncheckedCast(caches.putIfAbsent(name, cache));
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
        private final Map<File, V> locationCache = new ConcurrentHashMap<>();
        private final String name;
        private final FileSystemAccess fileSystemAccess;
        private final IndexedCache<HashCode, V> contentCache;
        private final Calculator<? extends V> calculator;

        DefaultFileContentCache(String name, FileSystemAccess fileSystemAccess, IndexedCache<HashCode, V> contentCache, Calculator<? extends V> calculator) {
            this.name = name;
            this.fileSystemAccess = fileSystemAccess;
            this.contentCache = contentCache;
            this.calculator = calculator;
        }

        @Override
        public void invalidateCachesFor(Iterable<String> affectedOutputPaths) {
            // A very dumb strategy for invalidating cache
            locationCache.clear();
        }

        @Override
        public V get(File file) {
            return locationCache.computeIfAbsent(file,
                location -> fileSystemAccess.readRegularFileContentHash(location.getAbsolutePath())
                    .map(contentHash -> contentCache.get(contentHash, key -> calculator.calculate(location, true))
                ).orElseGet(
                    () -> calculator.calculate(location, false)
                ));
        }

        private void assertStoredIn(IndexedCache<HashCode, V> store) {
            if (this.contentCache != store) {
                throw new IllegalStateException("Cache " + name + " cannot be recreated with different parameters");
            }
        }
    }
}
