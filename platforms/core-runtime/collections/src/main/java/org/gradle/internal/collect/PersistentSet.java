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

import static org.gradle.internal.collect.Preconditions.keyCannotBeNull;

/// A fully persistent hash-set implemented as a
/// [Compressed Hash-Array Mapped Prefix-tree](https://michael.steindorfer.name/publications/oopsla15.pdf).
///
/// @param <K> the type of keys, instances must provide proper implementations for [Object#equals] and [Object#hashCode]
public interface PersistentSet<K> extends Iterable<K> {

    /// Returns the single empty persistent set instance.
    @SuppressWarnings("unchecked")
    static <K> PersistentSet<K> of() {
        return (PersistentSet<K>) PersistentSet0.INSTANCE;
    }

    /// Returns a new persistent set containing the given key.
    static <K> PersistentSet<K> of(K key) {
        keyCannotBeNull(key);
        return new PersistentSet1<>(key);
    }

    /// Returns a new persistent set containing the unique keys in the given array or the [empty set][#of] when the given array is empty.
    @SuppressWarnings("unchecked")
    static <K> PersistentSet<K> of(K... keys) {
        PersistentSet<K> copy = of();
        for (K key : keys) {
            copy = copy.plus(key);
        }
        return copy;
    }

    /// Returns a new persistent set containing the unique keys produced by the given iterator or the [empty set][#of] when the iterator is empty.
    ///
    /// As an optimization, no copy is made when the given instance already represents a [PersistentSet].
    static <K> PersistentSet<K> copyOf(Iterable<K> keys) {
        if (keys instanceof PersistentSet<?>) {
            return (PersistentSet<K>) keys;
        }
        PersistentSet<K> copy = of();
        for (K key : keys) {
            copy = copy.plus(key);
        }
        return copy;
    }

    /// Returns a new persistent set containing all the keys from this set plus the given key,
    /// unless this set already [contains][#contains] the given key,
    /// in which case this set is returned.
    ///
    /// *~O(1)*
    PersistentSet<K> plus(K key);

    /// Returns a new persistent set with the given key removed from this set,
    /// unless the given key is not [present][#contains],
    /// in which case this set is returned.
    ///
    /// *~O(1)*
    PersistentSet<K> minus(K key);

    /// Returns a new persistent set with the given keys removed from this set or:
    /// - the [empty set][#of] when no keys are left;
    /// - this set when all keys are already absent from it.
    ///
    /// *~O(N)*
    ///
    /// @see #except(PersistentSet)
    PersistentSet<K> minusAll(Iterable<K> keys);

    /// Returns whether the given key is present in this set.
    /// *~O(1)*
    boolean contains(K key);

    /// Returns how many keys are present in this set.
    int size();

    /// Returns whether this set is the [empty set][#of].
    boolean isEmpty();

    /// Returns a new persistent set containing all unique keys from this set and the other set combined.
    ///
    /// If this set already contains all keys from the other set, then this set is returned.
    /// If the other set already contains all keys from this set, then the other set is returned.
    PersistentSet<K> union(PersistentSet<K> other);

    /// Returns a new persistent set containing only the keys that are present in both sets.
    ///
    /// If the sets are equal, this set is returned.
    /// If either set is the [empty set][#of], the [empty set][#of] is returned.
    PersistentSet<K> intersect(PersistentSet<K> other);

    /// Returns a new persistent set with all keys from this set that are also not present in the other set or:
    /// - the [empty set][#of] when the other set is a superset of this set;
    /// - this set when all keys from the other set are already absent from this set.
    ///
    /// @see #minusAll(Iterable)
    PersistentSet<K> except(PersistentSet<K> other);
}
