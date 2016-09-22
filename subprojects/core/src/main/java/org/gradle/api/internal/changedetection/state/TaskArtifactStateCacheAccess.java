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
import org.gradle.cache.PersistentStore;
import org.gradle.internal.serialize.Serializer;

/**
 * Provides access to the task history cache.
 */
public interface TaskArtifactStateCacheAccess extends PersistentStore {
    /**
     * Creates an indexed cache implementation. This cache may be used from any thread in any process.
     *
     * <p>Keys and values must be immutable, as they may be shared across multiple threads.
     *
     * <p>Updates are visible to all threads in this process as soon as they are made. Updates are visible to threads in other processes some point after they are made.
     */
    <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer);

    /**
     * Flushes any pending changes to disk.
     */
    void flush();
}
