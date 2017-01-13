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
package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.serialize.Serializer;

/**
 * Provides access to the persistent task history store.
 */
public interface TaskHistoryStore {
    /**
     * See {@link org.gradle.cache.PersistentStore#createCache(String, Class, Serializer)} for more details.
     *
     * @param maxEntriesToKeepInMemory The max number of entries to keep in memory, scaled according to available heap.
     * @param cacheInMemoryForShortLivedProcesses When true, entries are cached in memory. When false, entries are cached in memory only when it possible that another build will be run in this process.
     */
    <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses);

    void flush();
}
