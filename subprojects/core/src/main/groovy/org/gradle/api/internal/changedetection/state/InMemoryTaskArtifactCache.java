/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.PersistentIndexedCacheParameters;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class InMemoryTaskArtifactCache implements InMemoryPersistentCacheDecorator {

    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);

    private final Object lock = new Object();
    private final Map<File, CacheData> cache = new HashMap<File, CacheData>();

    private static final Map<String, Integer> CACHE_CAPS = new HashMap<String, Integer>();

    static {
        //it's the simplest implementation, not very nice
        //at very least, the max size should be provided at creation, by the creator of the cache
        //however, max size is a bit awkward in general and we should look into other options,
        //like using the Weighter and relate the cache size to the available heap, etc.
        CACHE_CAPS.put("fileSnapshots.bin", 10000);
        CACHE_CAPS.put("taskArtifacts.bin", 2000);
        CACHE_CAPS.put("outputFileStates.bin", 3000);
        CACHE_CAPS.put("fileHashes.bin", 140000);

        //In general, the in-memory cache must be capped at some level, otherwise it is reduces performance in truly gigantic builds
    }

    private <K, V> PersistentIndexedCache memCached(File cacheFile, PersistentIndexedCache<K, V> target) {
        synchronized (lock) {
            CacheData data;
            if (this.cache.containsKey(cacheFile)) {
                data = this.cache.get(cacheFile);
            } else {
                Integer maxSize = CACHE_CAPS.get(cacheFile.getName());
                assert maxSize != null : "Unknown cache.";
                data = new CacheData(cacheFile, maxSize);
                this.cache.put(cacheFile, data);
            }
            return new MapCache(target, data);
        }
    }

    private class CacheData {
        private File expirationMarker;
        private long marker;
        private final Cache data;

        public CacheData(File expirationMarker, int maxSize) {
            this.expirationMarker = expirationMarker;
            this.marker = expirationMarker.lastModified();
            this.data = CacheBuilder.newBuilder().maximumSize(maxSize).build();
        }

        public void storeMarker() {
            marker = expirationMarker.lastModified();
        }

        public void maybeExpire() {
            if (marker != expirationMarker.lastModified()) {
                LOG.info("Discarding {} in-memory cache values for {}", data.size(), expirationMarker);
                data.invalidateAll();
            }
        }
    }

    public PersistentCache withMemoryCaching(final PersistentCache target) {
        StringBuilder sb = new StringBuilder();
        for (CacheData data : cache.values()) {
            data.maybeExpire();
            sb.append(data.expirationMarker.getName()).append(":").append(data.data.size()).append(", ");
        }
        if (!cache.isEmpty()) {
            LOG.info("In-memory task history cache contains {} entries: {}", cache.size(), sb);
        }
        return new PersistentCache() {
            public File getBaseDir() {
                return target.getBaseDir();
            }

            public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
                PersistentIndexedCache<K, V> out = target.createCache(parameters);
                return memCached(parameters.getCacheFile(), out);
            }

            public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
                beforeUnlocked();
                try {
                    return target.longRunningOperation(operationDisplayName, action);
                } finally {
                    beforeLocked();
                }
            }

            public <T> T useCache(final String operationDisplayName, final Factory<? extends T> action) {
                return target.useCache(operationDisplayName, new Factory<T>() {
                    public T create() {
                        beforeLocked();
                        try {
                            return target.useCache(operationDisplayName, action);
                        } finally {
                            beforeUnlocked();
                        }
                    }
                });
            }

            public void useCache(String operationDisplayName, Runnable action) {
                useCache(operationDisplayName, Factories.toFactory(action));
            }

            public void longRunningOperation(String operationDisplayName, Runnable action) {
                longRunningOperation(operationDisplayName, Factories.toFactory(action));
            }
        };
    }

    private void beforeLocked() {
        synchronized (lock) {
            for (CacheData data : cache.values()) {
                data.maybeExpire();
            }
        }
    }

    private void beforeUnlocked() {
        synchronized (lock) {
            for (CacheData data : cache.values()) {
                data.storeMarker();
            }
        }
    }

    private static class MapCache<K, V> implements PersistentIndexedCache<K, V> {

        private final PersistentIndexedCache delegate;
        private final CacheData cache;

        public MapCache(PersistentIndexedCache delegate, CacheData cache) {
            this.delegate = delegate;
            this.cache = cache;
        }

        public V get(K key) {
            assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
            Value<V> value = (Value) cache.data.getIfPresent(key);
            if (value != null) {
                return value.value;
            }
            Object out = delegate.get(key);
            cache.data.put(key, new Value(out));
            return (V) out;
        }

        public void put(K key, V value) {
            cache.data.put(key, new Value<V>(value));
            delegate.put(key, value);
        }

        public void remove(K key) {
            cache.data.invalidate(key);
            delegate.remove(key);
        }

        private static class Value<T> {
            private T value;

            public Value(T value) {
                this.value = value;
            }
        }
    }
}
