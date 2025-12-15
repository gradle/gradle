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

import java.util.Map;
import java.util.function.BiFunction;

import static org.gradle.internal.collect.Preconditions.entryCannotBeNull;

/// A fully persistent hash-map implemented as a
/// [Compressed Hash-Array Mapped Prefix-tree](https://michael.steindorfer.name/publications/oopsla15.pdf).
///
/// @param <K> the type of keys, instances must provide proper implementations for [Object#equals] and [Object#hashCode]
/// @param <V> the type of values, as an optimization, instances will be compared for [equality][Object#equals] when [#assoc] is called with an existing key
public interface PersistentMap<K, V> extends Iterable<Map.Entry<K, V>> {

    /// Returns the single empty map instance.
    @SuppressWarnings("unchecked")
    static <K, V> PersistentMap<K, V> of() {
        return (PersistentMap<K, V>) PersistentMap0.INSTANCE;
    }

    /// Returns a map with the given key mapped to the given value.
    static <K, V> PersistentMap<K, V> of(K key, V value) {
        entryCannotBeNull(key, value);
        return new PersistentMap1<>(key, value);
    }

    /// Returns a map with the iterated entries or the [empty map][#of] when the iterator is empty.
    ///
    /// As an optimization, no copy is made when the given instance already represents a [PersistentMap].
    @SuppressWarnings("unchecked")
    static <K, V> PersistentMap<K, V> copyOf(Iterable<Map.Entry<K, V>> entries) {
        if (entries instanceof PersistentMap) {
            return (PersistentMap<K, V>) entries;
        }
        PersistentMap<K, V> copy = of();
        for (Map.Entry<K, V> entry : entries) {
            copy = copy.assoc(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /// Returns a new persistent map with a mapping from the given key to the given value.
    ///
    /// Unless the given key is already mapped to an [equal][Object#equals] value,
    /// then this map is returned.
    PersistentMap<K, V> assoc(K key, V value);

    /// Returns a new persistent map with no mapping for the given key.
    ///
    /// Unless the given key has no mapping, then this map is returned.
    PersistentMap<K, V> dissoc(K key);

    /// Returns a new persistent map with the mapping for the given key modified by the given function.
    ///
    /// The function will receive the current mapping or `null` when there's none.
    ///
    /// The value returned by the function will be the new mapping, unless the function returns `null`, in which case no mapping
    /// for the given key will be preserved.
    ///
    /// As an optimization, this map is returned whenever the new mapping is the same or [equal][Object#equals] to the previous mapping.
    PersistentMap<K, V> modify(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> function);

    /// Returns the current mapping for the given key or `null` when there's none.
    @Nullable V get(K key);

    ///  Returns the current mapping for the given key or the given default value when there's none.
    V getOrDefault(K key, V defaultValue);

    /// Returns `true` when this map contains a mapping for the given key.
    boolean containsKey(K key);

    /// Returns how many mappings are present in this map.
    int size();

    /// Returns whether this is the [empty map][#of].
    boolean isEmpty();
}
