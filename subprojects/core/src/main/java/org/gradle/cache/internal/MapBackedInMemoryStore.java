/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.Maps;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStore;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class MapBackedInMemoryStore implements PersistentStore, CacheAccess {
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
        return new CacheImpl<K, V>(lock);
    }

    @Override
    public void flush() {
    }

    @Override
    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        lock.lock();
        try {
            return action.create();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void useCache(String operationDisplayName, Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return action.create();
    }

    @Override
    public void longRunningOperation(String operationDisplayName, Runnable action) {
        action.run();
    }

    private static class CacheImpl<K, V> implements PersistentIndexedCache<K, V> {
        private final ReentrantLock lock;
        Map<K, V> entries = Maps.newHashMap();

        CacheImpl(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public V get(K key) {
            lock.lock();
            try {
                return entries.get(key);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void put(K key, V value) {
            lock.lock();
            try {
                entries.put(key, value);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void remove(K key) {
            lock.lock();
            try {
                entries.remove(key);
            } finally {
                lock.unlock();
            }
        }
    }
}
