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

/// A fully persistent array implemented as a _persistent bitmapped vector trie_, providing effective *O(1)* mutation and random access,
/// and efficient iteration.
///
/// See [PersistentArrayTrie] for implementation details.
///
public interface PersistentArray<T> extends Iterable<T> {

    /// Returns the single empty persistent array instance.
    @SuppressWarnings("unchecked")
    static <T> PersistentArray<T> of() {
        return (PersistentArray<T>) PersistentArray0.INSTANCE;
    }

    /// Returns a new persistent array with the given value.
    static <T> PersistentArray<T> of(T value) {
        return new PersistentArray1<>(value);
    }

    /// Returns a new persistent array with the given values or the [empty array][#of] when the given array is empty.
    @SuppressWarnings("unchecked")
    static <T> PersistentArray<T> of(T... values) {
        PersistentArray<T> copy = of();
        for (T value : values) {
            copy = copy.plus(value);
        }
        return copy;
    }

    /// Returns a new persistent array with the iterated values or the [empty array][#of] when the iterator is empty.
    ///
    /// As an optimization, no copy is made when the given instance already represents a [PersistentArray].
    static <T> PersistentArray<T> copyOf(Iterable<T> values) {
        if (values instanceof PersistentArray<?>) {
            return (PersistentArray<T>) values;
        }
        PersistentArray<T> copy = of();
        for (T value : values) {
            copy = copy.plus(value);
        }
        return copy;
    }

    /// Returns a new array with the given value appended.
    ///
    /// *~O(1)*
    PersistentArray<T> plus(T value);

    /// Returns how many elements are present in the array.
    ///
    /// *O(1)*
    int size();

    /// Returns the element at the given index.
    ///
    /// *~O(1)*
    ///
    /// @throws IndexOutOfBoundsException when the given index is not between 0 and [size][#size()] - 1 or this is the [empty array][#of].
    T get(int index);

    /// Returns the last element [appended][#plus] to the array,
    /// unless this is the [empty array][#of], in which case it returns `null`.
    ///
    /// *O(1)*
    @Nullable
    T getLast();

    /// Returns whether this array is the [empty array][#of].
    ///
    /// *O(1)*
    boolean isEmpty();

    /// Returns whether the given value is [equal][Object#equals] to any element in this array.
    ///
    /// *~O(N)*
    boolean contains(T value);
}
