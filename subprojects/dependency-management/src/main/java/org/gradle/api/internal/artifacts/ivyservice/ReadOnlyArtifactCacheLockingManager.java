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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.function.Function;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

/**
 * An implementation of an artifact cache manager which performs operations in a read-only
 * cache first. If the operation is not successful in the readonly cache OR if it's a write
 * operation, the 2nd level writable cache is used.
 *
 * Operations use in-process locking for the read-only cache (even when requesting file locking) and
 * write operations use the regular locking mechanism (file or in-process).
 */
public class ReadOnlyArtifactCacheLockingManager implements ArtifactCacheLockingManager, Closeable {
    private final static Logger LOGGER = Logging.getLogger(ReadOnlyArtifactCacheLockingManager.class);

    private final PersistentCache cache;

    public ReadOnlyArtifactCacheLockingManager(CacheRepository cacheRepository,
                                               ArtifactCacheMetadata cacheMetaData) {
        cache = cacheRepository
            .cache(cacheMetaData.getCacheDir())
            .withCrossVersionCache(CacheBuilder.LockTarget.CacheDirectory)
            .withDisplayName("read only artifact cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything, it's read-only
            .open();
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public <T> T withFileLock(Factory<? extends T> action) {
        return cache.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cache.withFileLock(action);
    }

    @Override
    public <T> T useCache(Factory<? extends T> action) {
        return cache.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        PersistentIndexedCacheParameters<K, V> parameters = PersistentIndexedCacheParameters.of(cacheFileInMetaDataStore, keySerializer, valueSerializer);
        if (cache.cacheExists(parameters)) {
            return new TransparentCacheLockingPersistentCache<>(new FailSafePersistentCache<>(cache.createCache(parameters)));
        }
        return new EmptyCache<>();
    }

    private static class EmptyCache<K, V> implements PersistentIndexedCache<K, V> {
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

    private static class FailSafePersistentCache<K, V> implements PersistentIndexedCache<K, V> {
        private final PersistentIndexedCache<K, V> delegate;
        private boolean failed;

        private FailSafePersistentCache(PersistentIndexedCache<K, V> delegate) {
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

    private class TransparentCacheLockingPersistentCache<K, V> implements PersistentIndexedCache<K, V> {
        private final PersistentIndexedCache<K, V> persistentCache;

        public TransparentCacheLockingPersistentCache(PersistentIndexedCache<K, V> persistentCache) {
            this.persistentCache = persistentCache;
        }

        @Nullable
        @Override
        public V getIfPresent(final K key) {
            return cache.useCache(() -> persistentCache.getIfPresent(key));
        }

        @Override
        public V get(final K key, final Function<? super K, ? extends V> producer) {
            return cache.useCache(() -> persistentCache.get(key, producer));
        }

        @Override
        public void put(final K key, final V value) {
            cache.useCache(() -> persistentCache.put(key, value));
        }

        @Override
        public void remove(final K key) {
            cache.useCache(() -> persistentCache.remove(key));
        }
    }
}
