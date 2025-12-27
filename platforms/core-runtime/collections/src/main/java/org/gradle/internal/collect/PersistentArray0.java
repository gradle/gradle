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

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

import static org.gradle.internal.collect.PersistentArrayTrie.indexOutOfBounds;

/// The [empty][PersistentArray#of()] [PersistentArray] implementation.
///
final class PersistentArray0 implements PersistentArray<Object> {

    static final PersistentArray<Object> INSTANCE = new PersistentArray0();

    private PersistentArray0() {
    }

    @Override
    public PersistentArray<Object> plus(Object value) {
        return new PersistentArray1<>(value);
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean contains(Object value) {
        return false;
    }

    @Override
    public Object get(int index) {
        throw indexOutOfBounds(index);
    }

    @Override
    public @Nullable Object getLast() {
        return null;
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public void forEach(Consumer<? super Object> action) {
    }

    @Override
    public String toString() {
        return "[]";
    }
}
