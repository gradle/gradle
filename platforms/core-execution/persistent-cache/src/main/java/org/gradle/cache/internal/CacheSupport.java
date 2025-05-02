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
package org.gradle.cache.internal;

import org.gradle.cache.Cache;

import java.util.function.Function;

public abstract class CacheSupport<K, V> implements Cache<K, V> {

    @Override
    public V get(K key, Function<? super K, ? extends V> factory) {
        V value = doGet(key);
        if (value == null) {
            value = factory.apply(key);
            doCache(key, value);
        }

        return value;
    }

    @Override
    public V getIfPresent(K key) {
        return doGet(key);
    }

    @Override
    public void put(K key, V value) {
        doCache(key, value);
    }

    abstract protected <T extends K> V doGet(T key);

    abstract protected <T extends K, N extends V> void doCache(T key, N value);
}
