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

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import static org.gradle.internal.collect.PersistentArrayTrie.indexOutOfBounds;

/// A [PersistentArray] with a single element.
///
final class PersistentArray1<T> implements PersistentArray<T> {

    private final T value;

    public PersistentArray1(T value) {
        this.value = value;
    }

    @Override
    public PersistentArray<T> plus(T value) {
        return new PersistentArraySmall<>(new Object[]{this.value, value});
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
    public boolean contains(T value) {
        return Objects.equals(value, this.value);
    }

    @Override
    public T get(int index) {
        if (index != 0) {
            throw indexOutOfBounds(index);
        }
        return value;
    }

    @Override
    public T getLast() {
        return value;
    }

    @Override
    public Iterator<T> iterator() {
        return new SingletonIterator<>(value);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        action.accept(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        // ✅ Only compares with PersistentArray1. Safe because PersistentArray implementations
        // have non-overlapping size ranges (1, 2-32, 33+), so same-content arrays always have the same type.
        if (obj instanceof PersistentArray1) {
            return ((PersistentArray1<?>) obj).value.equals(value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "[" + value + "]";
    }
}
