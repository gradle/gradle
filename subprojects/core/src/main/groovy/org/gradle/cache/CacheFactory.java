/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.cache;

import org.gradle.CacheUsage;

import java.io.File;
import java.util.Map;

public interface CacheFactory {
    /**
     * Opens a cache. It is the caller's responsibility to call {@link org.gradle.cache.CacheFactory.CacheReference#release()} when finished with the cache.
     */
    CacheReference<PersistentCache> open(File cacheDir, CacheUsage usage, Map<String, ?> properties);

    /**
     * Opens a cache. It is the caller's responsibility to call {@link org.gradle.cache.CacheFactory.CacheReference#release()} when finished with the cache.
     */
    <E> CacheReference<PersistentStateCache<E>> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<E> serializer);

    /**
     * Opens a cache. It is the caller's responsibility to call {@link org.gradle.cache.CacheFactory.CacheReference#release()} when finished with the cache.
     */
    <K, V> CacheReference<PersistentIndexedCache<K, V>> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<V> serializer);

    interface CacheReference<T> {
        T getCache();

        void release();
    }
}




