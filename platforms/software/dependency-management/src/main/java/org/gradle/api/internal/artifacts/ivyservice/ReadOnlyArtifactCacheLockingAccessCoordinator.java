/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An implementation of an artifact cache manager which performs operations in a read-only
 * cache first. If the operation is not successful in the readonly cache OR if it's a write
 * operation, the 2nd level writable cache is used.
 *
 * Operations use in-process locking for the read-only cache (even when requesting file locking) and
 * write operations use the regular locking mechanism (file or in-process).
 */
public class ReadOnlyArtifactCacheLockingAccessCoordinator implements ArtifactCacheLockingAccessCoordinator, Closeable {
    private final static Logger LOGGER = Logging.getLogger(ReadOnlyArtifactCacheLockingAccessCoordinator.class);

    private final PersistentCache cache;

    public ReadOnlyArtifactCacheLockingAccessCoordinator(
            UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
            ArtifactCacheMetadata cacheMetaData) {
        cache = unscopedCacheBuilderFactory
            .cache(cacheMetaData.getCacheDir())
            .withDisplayName("read only artifact cache")
            .withInitialLockMode(FileLockManager.LockMode.None) // Don't need to lock anything, it's read-only
            .open();
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public <T> T withFileLock(Supplier<? extends T> action) {
        return cache.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cache.withFileLock(action);
    }

    @Override
    public <T> T useCache(Supplier<? extends T> action) {
        return cache.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public <K, V> IndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        IndexedCacheParameters<K, V> parameters = IndexedCacheParameters.of(cacheFileInMetaDataStore, keySerializer, valueSerializer);
        if (cache.indexedCacheExists(parameters)) {
            return new TransparentCacheLockingIndexedCache<>(new FailSafeIndexedCache<>(cache.createIndexedCache(parameters)));
        }
        return new EmptyIndexedCache<>();
    }

    private static class EmptyIndexedCache<K, V> implements IndexedCache<K, V> {
        @Nullable
        @Override
        public V getIfPresent(K key) {
            return null;
        }

        @Override
        public V get(K key, Function<? super K, ? extends V> producer) {
            return producer.apply(key);
        }

        @Override
        public void put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(K key) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FailSafeIndexedCache<K, V> implements IndexedCache<K, V> {
        private final IndexedCache<K, V> delegate;
        private boolean failed;

        private FailSafeIndexedCache(IndexedCache<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override
        @Nullable
        public V getIfPresent(K key) {
            return failSafe(() -> delegate.getIfPresent(key));
        }

        @Override
        public V get(K key, Function<? super K, ? extends V> producer) {
            return failSafe(() -> delegate.get(key, producer));
        }

        @Override
        public void put(K key, V value) {
        }

        @Override
        public void remove(K key) {
        }

        private <T> T failSafe(Factory<T> operation) {
            if (failed) {
                return null;
            }
            try {
                return operation.create();
            } catch (Exception ex) {
                failed = true;
                LOGGER.debug("Error accessing read-only cache", ex);
            }
            return null;
        }

    }

    private class TransparentCacheLockingIndexedCache<K, V> implements IndexedCache<K, V> {
        private final IndexedCache<K, V> indexedCache;

        public TransparentCacheLockingIndexedCache(IndexedCache<K, V> indexedCache) {
            this.indexedCache = indexedCache;
        }

        @Nullable
        @Override
        public V getIfPresent(final K key) {
            return cache.useCache(() -> indexedCache.getIfPresent(key));
        }

        @Override
        public V get(final K key, final Function<? super K, ? extends V> producer) {
            return cache.useCache(() -> indexedCache.get(key, producer));
        }

        @Override
        public void put(final K key, final V value) {
            cache.useCache(() -> indexedCache.put(key, value));
        }

        @Override
        public void remove(final K key) {
            cache.useCache(() -> indexedCache.remove(key));
        }
    }
}
