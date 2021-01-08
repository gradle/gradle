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

package org.gradle.cache;

import org.gradle.internal.serialize.Serializer;

/**
 * Represents some data store, made up of zero or more {@link PersistentIndexedCache} instances.
 *
 * <p>A store may be shared between processes or may be private to this process. When private to this process, the store is transient and the contents are lost when the process exits.</p>
 *
 * <p>Stores are safe for concurrent access by multiple threads and multiple processes, when shared.</p>
 */
public interface PersistentStore {
    /**
     * Opens an indexed cache in this store, creating it if it does not exist.
     *
     * <p>Keys and values must be immutable, as they may be shared by multiple threads.
     *
     * <p>The indexed cache may be safely used by multiple threads concurrently. Updates are visible to all threads in this process as soon as they are made.
     *
     * <p>The indexed cache may be shared between processes. When shared, the indexed cache may be safely used by multiple processes concurrently. Updates are visible to threads in other processes some point after they are made.</p>
     */
    <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer);

    /**
     * Flushes any pending changes. This makes the changes persistent and makes them visible to other processes, as relevant for this store. Blocks until complete.
     */
    void flush();
}
