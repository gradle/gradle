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

import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used by {@link DefaultFileContentCacheFactory} to maintain caches across builds.
 */
public class FileContentCacheBackingStore {
    private final Map<String, com.google.common.cache.Cache> caches = new ConcurrentHashMap<String, com.google.common.cache.Cache>();
    private final HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();

    <V> com.google.common.cache.Cache<HashCode, V> getStore(String name, int normalizedCacheSize) {
        com.google.common.cache.Cache cache = caches.get(name);
        if (cache == null) {
            cache = CacheBuilder.newBuilder().maximumSize(cacheSizer.scaleCacheSize(normalizedCacheSize)).build();
            caches.put(name, cache);
        }
        return cache;
    }
}
