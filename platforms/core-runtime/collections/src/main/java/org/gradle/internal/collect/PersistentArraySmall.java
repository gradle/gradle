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

import org.gradle.util.internal.ArrayUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

/// A small [PersistentArray] holding at least 2 elements and at most [32][PersistentArrayTrie#WIDTH] elements.
///
final class PersistentArraySmall<T> implements PersistentArray<T> {

    private final Object[] array;

    public PersistentArraySmall(Object[] array) {
        assert array.length > 1 && array.length <= PersistentArrayTrie.WIDTH;
        this.array = array;
    }

    @Override
    public PersistentArray<T> plus(T value) {
        return array.length < PersistentArrayTrie.WIDTH
            ? new PersistentArraySmall<>(ArrayCopy.append(array, value))
            : new PersistentArrayTrie<>(PersistentArrayTrie.WIDTH + 1, 0, array, new Object[]{value});
    }

    @Override
    public int size() {
        return array.length;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(int index) {
        return (T) array[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getLast() {
        return (T) array[array.length - 1];
    }

    @Override
    public boolean contains(T value) {
        return ArrayUtils.contains(array, value);
    }

    @Override
    public Iterator<T> iterator() {
        return new ArrayIterator<>(array);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void forEach(Consumer<? super T> action) {
        for (Object o : array) {
            action.accept((T) o);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        // ✅ Only compares with PersistentArraySmall. Safe because PersistentArray implementations
        // have non-overlapping size ranges (1, 2-32, 33+), so same-content arrays always have the same type.
        if (obj instanceof PersistentArraySmall) {
            PersistentArraySmall<?> other = (PersistentArraySmall<?>) obj;
            return Arrays.equals(array, other.array);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    @Override
    public String toString() {
        return ToString.nonEmptyIterator(iterator());
    }
}
