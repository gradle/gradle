/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect;

import java.util.Map;
import java.util.Objects;

/// An immutable [Map.Entry] from a [PersistentMap].
final class PersistentMapEntry<K, V> implements Map.Entry<K, V> {

    static <K, V> int hashCodeOf(K k, V v) {
        return Objects.hashCode(k) ^ Objects.hashCode(v);
    }

    private final K key;
    private final V val;

    public PersistentMapEntry(K key, V val) {
        this.key = key;
        this.val = val;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return val;
    }

    @Override
    public V setValue(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return hashCodeOf(key, val);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Map.Entry)) {
            return false;
        }
        Map.Entry<?, ?> that = (Map.Entry<?, ?>) other;
        return Objects.equals(key, that.getKey())
            && Objects.equals(val, that.getValue());
    }

    @Override
    public String toString() {
        return ToString.entry(key, val);
    }
}
