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
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class InMemoryTaskArtifactCache implements InMemoryPersistentCacheDecorator {

    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);

    private final Object lock = new Object();
    private final Map<File, Cache> cache = new HashMap<File, Cache>();
    private final Map<File, FileLock.State> states = new HashMap<File, FileLock.State>();

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

    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> withMemoryCaching(final File cacheFile, final MultiProcessSafePersistentIndexedCache<K, V> original) {
        final Cache data;
        synchronized (lock) {
            if (this.cache.containsKey(cacheFile)) {
                data = this.cache.get(cacheFile);
                LOG.info("In-memory cache of {}: Size{{}}, {}", cacheFile.getName(), data.size(), data.stats());
            } else {
                Integer maxSize = CACHE_CAPS.get(cacheFile.getName());
                assert maxSize != null : "Unknown cache.";
                data = CacheBuilder.newBuilder().maximumSize(maxSize).build();
                this.cache.put(cacheFile, data);
            }
        }
        return new MultiProcessSafePersistentIndexedCache<K, V>() {
            public void close() {
                original.close();
            }

            public V get(K key) {
                assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
                Value<V> value = (Value) data.getIfPresent(key);
                if (value != null) {
                    return value.value;
                }
                Object out = original.get(key);
                data.put(key, new Value(out));
                return (V) out;
            }

            public void put(K key, V value) {
                data.put(key, new Value<V>(value));
                original.put(key, value);
            }

            public void remove(K key) {
                data.invalidate(key);
                original.remove(key);
            }

            public void onStartWork(String operationDisplayName, FileLock.State currentCacheState) {
                boolean outOfDate;
                synchronized (lock) {
                    FileLock.State previousState = states.get(cacheFile);
                    outOfDate = previousState == null || currentCacheState.hasBeenUpdatedSince(previousState);
                }

                if (outOfDate) {
                    LOG.info("Invalidating in-memory cache of {}", cacheFile);
                    data.invalidateAll();
                }
            }

            public void onEndWork(FileLock.State currentCacheState) {
                synchronized (lock) {
                    states.put(cacheFile, currentCacheState);
                }
            }
        };
    }

    private static class Value<T> {
        private T value;

        public Value(T value) {
            this.value = value;
        }
    }
}
