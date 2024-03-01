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
package org.gradle.cache;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Cache<K, V>  {
    /**
     * Locates the given entry, using the supplied factory when the entry is not present or has been discarded, to recreate the entry in the cache.
     *
     * <p>Implementations may prevent more than one thread calculating the same key at the same time or not.
     */
    V get(K key, Function<? super K, ? extends V> factory);

    default V get(K key, Supplier<? extends V> supplier) {
        return get(key, __ -> supplier.get());
    }

    /**
     * Locates the given entry, if present. Returns {@code null} when missing.
     */
    @Nullable
    V getIfPresent(K key);

    /**
     * Adds the given value to the cache, replacing any existing value.
     */
    void put(K key, V value);
}
