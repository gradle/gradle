/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal


import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import java.util.function.Predicate

class TestCrossBuildInMemoryCacheFactory implements CrossBuildInMemoryCacheFactory {
    private final static CrossBuildInMemoryCacheFactory INSTANCE = new TestCrossBuildInMemoryCacheFactory()

    static CrossBuildInMemoryCacheFactory instance() {
        INSTANCE
    }

    @Override
    <K, V> CrossBuildInMemoryCache<K, V> newCache() {
        return new TestCache<K, V>()
    }

    @Override
    <K, V> CrossBuildInMemoryCache<K, V> newCacheRetainingDataFromPreviousBuild(Predicate<V> retentionFilter) {
        return new TestCache<K, V>()
    }

    @Override
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache() {
        return new TestCache<Class<?>, V>()
    }

    @Override
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassMap() {
        return new TestCache<Class<?>, V>()
    }

    static class TestCache<K, V> implements CrossBuildInMemoryCache<K, V> {
        private final ReentrantLock lock = new ReentrantLock(true)
        private final Map<K, V> values = new ConcurrentHashMap<>()

        @Override
        V getIfPresent(K key) {
            return values.get(key)
        }

        @Override
        V get(K key, Function<? super K, ? extends V> factory) {
            try {
                // Using a lock instead of computeIfAbsent, since with
                // computeIfAbsent we can get recursive update exception.
                lock.lock()
                def v = values.get(key)
                if (v == null) {
                    v = factory.apply(key)
                    values.put(key, v)
                }
                return v
            } finally {
                lock.unlock()
            }
        }

        @Override
        void put(K key, V value) {
            values.put(key, value)
        }

        @Override
        void clear() {
            values.clear()
        }
    }
}
