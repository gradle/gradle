/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.cache.Cache;
import org.gradle.cache.PersistentIndexedCache;

import java.util.function.Function;

public class MinimalPersistentCache<K, V> implements Cache<K, V> {
    private final PersistentIndexedCache<K, V> cache;

    public MinimalPersistentCache(PersistentIndexedCache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> factory) {
        V cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        V value = factory.apply(key); //don't synchronize value creation
        //we could potentially avoid creating value that is already being created by a different thread.

        cache.put(key, value);
        return value;
    }

    @Override
    public V getIfPresent(K value) {
        return cache.getIfPresent(value);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }
}
