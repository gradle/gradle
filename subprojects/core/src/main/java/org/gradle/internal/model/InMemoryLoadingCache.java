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

/**
 * A cache that loads values on demand as they are requested.
 */
public interface InMemoryLoadingCache<K, V> {

    /**
     * Get the value corresponding to the given key, loading the value
     * on demand if it is not already present in the cache.
     *
     * @param key The key to look up.
     *
     * @return The value corresponding to the key.
     */
    V get(K key);

    /**
     * Invalidates the cache, clearing all cached entries.
     */
    void invalidate();

}
