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
import java.util.NoSuchElementException;

/// [Iterator] over an [#array].
final class ArrayIterator<T> implements Iterator<T> {

    private final Object[] array;
    private int index = 0;

    ArrayIterator(Object[] array) {
        this.array = array;
    }

    @Override
    public boolean hasNext() {
        return index < array.length;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T next() {
        if (index == array.length) {
            throw new NoSuchElementException();
        }
        return (T) array[index++];
    }
}
