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
package org.gradle.api.internal.cache;

import org.gradle.api.internal.Factory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CacheAccessSerializer<K, V> implements Cache<K, V> {
    
    final private Lock lock = new ReentrantLock();
    final private Cache<K, V> cache;
    
    public CacheAccessSerializer(Cache<K, V> cache) {
        this.cache = cache;
    }

    public <T extends K> V get(T key, Factory<? extends V> factory) {
        lock.lock();
        try {
            return cache.get(key, factory);
        } finally {
            lock.unlock();
        }
    }

}