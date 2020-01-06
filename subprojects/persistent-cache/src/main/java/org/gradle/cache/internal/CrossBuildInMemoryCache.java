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

package org.gradle.cache.internal;

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Transformer;

import javax.annotation.Nullable;

/**
 * An in-memory cache of calculated values that are used across builds. The implementation takes care of cleaning up state that is no longer required.
 */
@ThreadSafe
public interface CrossBuildInMemoryCache<K, V> {
    /**
     * Locates the given entry, using the supplied factory when the entry is not present or has been discarded, to recreate the entry in the cache.
     *
     * <p>Implementations must prevent more than one thread calculating the same key at the same time.
     */
    V get(K key, Transformer<V, K> factory);

    /**
     * Locates the given entry, if present. Returns {@code null} when missing.
     */
    @Nullable V get(K key);

    /**
     * Adds the given value to the cache, replacing any existing value.
     */
    void put(K key, V value);

    /**
     * Removes all entries from this cache.
     */
    void clear();
}
