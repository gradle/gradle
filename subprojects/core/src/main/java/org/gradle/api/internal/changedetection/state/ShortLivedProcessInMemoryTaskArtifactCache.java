/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.cache.internal.MultiProcessSafeAsyncPersistentIndexedCache;

/**
 * InMemoryTaskArtifactCache suitable for non-daemon processes.
 *
 * Applies caching to file snapshots only. For all other caches, each entry is used at most once, so does not benefit from caching
 */
public class ShortLivedProcessInMemoryTaskArtifactCache extends InMemoryTaskArtifactCache {
    @Override
    protected <K, V> MultiProcessSafeAsyncPersistentIndexedCache<K, V> applyInMemoryCaching(String cacheId, String cacheName, MultiProcessSafeAsyncPersistentIndexedCache<K, V> backingCache) {
        // Apply in-memory caching to file snapshots only. For all other caches, each entry is used at most once, so does not benefit from caching
        if ("fileHashes".equals(cacheName)) {
            return super.applyInMemoryCaching(cacheId, cacheName, backingCache);
        }
        return backingCache;
    }
}
