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

import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Synchronizer;

public class CacheAccessSerializer<K, V> implements Cache<K, V> {

    final private Synchronizer synchronizer = new Synchronizer();
    final private Cache<K, V> cache;

    public CacheAccessSerializer(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public V get(final K key, final Factory<V> factory) {
        return synchronizer.synchronize(new Factory<V>() {
            @Override
            public V create() {
                return cache.get(key, factory);
            }
        });
    }

}
