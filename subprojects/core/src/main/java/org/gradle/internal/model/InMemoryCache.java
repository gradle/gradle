/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.model;

import java.util.function.Function;

/**
 * A cache that loads values as they are requested, from a loader provided at the time of lookup.
 * <p>
 * Prefer {@link InMemoryLoadingCache loading caches} to non-loading caches. Loading caches define their
 * loading functions _upon construction_ as opposed to _upon lookup_. This leads to two primary benefits:
 * <ul>
 *     <li>
 *         The loading function is guaranteed to be only a function of the key. This ensures the cached value
 *         does not depend on external state unrelated to its key.
 *     </li>
 *     <li>
 *         Unnecessary allocations are avoided. With a non-loading cache, the loading function must be allocated
 *         for each lookup, potentially capturing some state from the surrounding scope. For infrequently accessed
 *         caches this may not be much of a concern, but for frequently accessed caches this can lead to unnecessary
 *         allocations.
 *     </li>
 * </ul>
 *
 */
public interface InMemoryCache<K, V> {

    /**
     * Get the value corresponding to the given key, loading the value using the
     * provided the {@code loader} if it is not already present in the cache.
     *
     * @param key The key to look up.
     * @param loader Loads the corresponding value if it is not already present in the cache.
     *
     * @return The value corresponding to the key.
     */
    V computeIfAbsent(K key, Function<K, V> loader);

    /**
     * Invalidates the cache, clearing all cached entries.
     */
    void invalidate();

}
