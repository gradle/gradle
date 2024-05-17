/*
 * Copyright 2020 the original author or authors.
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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class ManualEvictionInMemoryCache<K, V> implements Cache<K, V> {
    // Use 256 as initial size to start out with enough concurrency.
    private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>(256);

    @Override
    public V get(K key, Function<? super K, ? extends V> factory) {
        return map.computeIfAbsent(key, factory);
    }

    @Override
    public V getIfPresent(K key) {
        return map.get(key);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    public void retainAll(Collection<? extends K> keysToRetain) {
        map.keySet().retainAll(keysToRetain);
    }

    public void clear() {
        map.clear();
    }
}
