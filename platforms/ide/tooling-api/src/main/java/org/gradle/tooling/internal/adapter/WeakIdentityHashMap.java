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

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Set;

/**
 * A specialized map wrapper, that uses weak references for keys and stores
 * values as strong references. It allows the garbage collector to collect keys when they are no longer in use.
 *
 * Keys are stored wrapped in {@code WeakIdentityHashMap.WeakKey} weak reference implementation, that uses {@code System.identityHashCode}
 * for generating hash code and considers referent equality for {@code equals} method.
 */
class WeakIdentityHashMap<K, V> {
    private final HashMap<WeakKey<K>, V> map = new HashMap<>();

    void put(K key, V value) {
        map.put(new WeakKey<>(key), value);
    }

    @Nullable
    V get(K key) {
        return map.get(new WeakKey<>(key));
    }

    Set<WeakKey<K>> keySet() {
        return map.keySet();
    }

    V computeIfAbsent(K key, AbsentValueProvider<V> absentValueProvider) {
        WeakKey<K> weakKey = new WeakKey<>(key);
        V value = map.get(weakKey);

        if (value == null) {
            value = absentValueProvider.provide();
            map.put(weakKey, value);
        }

        return value;
    }

    public interface AbsentValueProvider<T> {

        T provide();
    }

    public static class WeakKey<T> extends WeakReference<T> {
        private final int hashCode;

        public WeakKey(T referent) {
            super(referent);
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
            return hashCode;
        }
    }
}
