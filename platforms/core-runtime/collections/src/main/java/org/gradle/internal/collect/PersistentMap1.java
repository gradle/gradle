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

import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.gradle.internal.collect.Preconditions.entryCannotBeNull;

/// A [PersistentMap] with a single entry.
final class PersistentMap1<K, V> implements PersistentMap<K, V> {

    private final K key;
    private final V value;

    public PersistentMap1(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public PersistentMap<K, V> assoc(K key, V value) {
        entryCannotBeNull(key, value);
        if (containsKey(key)) {
            return Objects.equals(this.value, value)
                ? this
                : new PersistentMap1<>(key, value);
        }
        return assocNew(key, value);
    }

    @Override
    public PersistentMap<K, V> dissoc(K key) {
        return containsKey(key)
            ? PersistentMap.of()
            : this;
    }

    @Override
    public PersistentMap<K, V> modify(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> function) {
        if (containsKey(key)) {
            V val = function.apply(key, value);
            if (Objects.equals(value, val)) {
                return this;
            }
            return val != null
                ? new PersistentMap1<>(key, val)
                : PersistentMap.of();
        } else {
            V val = function.apply(key, null);
            return val != null
                ? assocNew(key, val)
                : this;
        }
    }

    private PersistentMap<K, V> assocNew(K key, V value) {
        return PersistentMapTrie.ofDistinct(key, value, this.key, this.value);
    }

    @Override
    public @Nullable V get(K key) {
        return containsKey(key) ? value : null;
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        return containsKey(key) ? value : defaultValue;
    }

    @Override
    public boolean containsKey(K key) {
        return this.key.equals(key);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new SingletonIterator<>(mapEntry());
    }

    @Override
    public void forEach(Consumer<? super Map.Entry<K, V>> action) {
        action.accept(mapEntry());
    }

    @Override
    public String toString() {
        return "{" + ToString.entry(key, value) + "}";
    }

    @Override
    public int hashCode() {
        return PersistentMapEntry.hashCodeOf(key, value);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof PersistentMap1<?, ?>)) {
            return false;
        }
        PersistentMap1<?, ?> that = (PersistentMap1<?, ?>) other;
        return that.size() == 1
            && Objects.equals(this.key, that.key)
            && Objects.equals(this.value, that.value);
    }

    private Map.Entry<K, V> mapEntry() {
        return new PersistentMapEntry<>(key, value);
    }
}
