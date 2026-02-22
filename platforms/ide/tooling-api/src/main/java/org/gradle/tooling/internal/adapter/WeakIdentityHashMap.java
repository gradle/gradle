/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import org.jspecify.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A specialized map wrapper, that uses weak references for keys and stores
 * values as strong references. It allows the garbage collector to collect keys when they are no longer in use.
 *
 * Keys are stored wrapped in {@code WeakIdentityHashMap.WeakKey} weak reference implementation, that uses {@code System.identityHashCode}
 * for generating hash code and considers referent equality for {@code equals} method.
 */
class WeakIdentityHashMap<K, V> {
    private final ConcurrentHashMap<WeakKey<K>, V> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> referenceQueue = new ReferenceQueue<>();

    void put(K key, V value) {
        cleanUnreferencedKeys();
        map.put(new WeakKey<>(key, referenceQueue), value);
    }

    @Nullable
    V get(K key) {
        cleanUnreferencedKeys();
        return map.get(new WeakKey<>(key));
    }

    Set<WeakKey<K>> keySet() {
        cleanUnreferencedKeys();
        return map.keySet();
    }

    V computeIfAbsent(K key, AbsentValueProvider<V> absentValueProvider) {
        cleanUnreferencedKeys();
        WeakKey<K> weakKey = new WeakKey<>(key);
        return map.computeIfAbsent(weakKey, k -> absentValueProvider.provide());
    }

    int size() {
        cleanUnreferencedKeys();
        return map.size();
    }

    @SuppressWarnings("unchecked")
    private void cleanUnreferencedKeys() {
        WeakKey<K> staleKey;
        while ((staleKey = (WeakKey<K>) referenceQueue.poll()) != null) {
            map.remove(staleKey);
        }
    }

    public interface AbsentValueProvider<T> {

        T provide();
    }

    public static class WeakKey<T> extends WeakReference<T> {
        private final int hashCode;

        public WeakKey(T referent) {
            this(referent, null);
        }

        public WeakKey(T referent, @Nullable ReferenceQueue<? super T> q) {
            super(referent, q);
            hashCode = System.identityHashCode(referent);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof WeakKey)) {
                return false;
            }
            Object thisReferent = get();
            Object objReferent = ((WeakKey<?>) obj).get();
            return thisReferent == objReferent;
        }

        @Override
        public int hashCode() {
            return get() != null ? hashCode : 0;
        }
    }
}
