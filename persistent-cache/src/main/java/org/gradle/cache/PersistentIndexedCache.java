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

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A persistent store of objects of type V indexed by a key of type K.
 */
public interface PersistentIndexedCache<K, V> extends Cache<K, V> {
    /**
     * Fetches the value mapped to the given key from this cache, blocking until it is available.
     *
     * A shared or exclusive file lock is held while fetching the value, depending on implementation.
     *
     * @return The value, or null if no value associated with the key.
     */
    @Override
    @Nullable
    V getIfPresent(K key);

    /**
     * Returns the value mapped to the given key, producing the value if not present.
     *
     * The implementation blocks when multiple threads producing the same value concurrently, so that only a single thread produces the value and the other threads reuse the result.
     *
     * Production of the value always happens synchronously by the calling thread. However, the implementation may update the backing store with new value synchronously or asynchronously.
     *
     * A file lock is held until the value has been produced and written to the persistent store, and other processes will be blocked from producing the same value until this process has completed doing so.
     *
     * @return The value.
     */
    @Override
    V get(K key, Function<? super K, ? extends V> producer);

    /**
     * Maps the given value to the given key, replacing any existing value.
     *
     * The implementation may do this synchronously or asynchronously. A file lock is held until the value has been written to the persistent store.
     */
    @Override
    void put(K key, V value);

    /**
     * Removes a key-value mapping from this cache. A shared lock is held while updating the value.
     *
     * The implementation may do this synchronously or asynchronously. A file lock is held until the value has been removed from the persistent store.
     */
    void remove(K key);
}
