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

/// [Iterator] over a single [#value].
final class SingletonIterator<T> implements Iterator<T> {

    private final T value;
    private int index = 0;

    public SingletonIterator(T value) {
        this.value = value;
    }

    @Override
    public boolean hasNext() {
        return index == 0;
    }

    @Override
    public T next() {
        if (index != 0) {
            throw new NoSuchElementException();
        }
        ++index;
        return value;
    }
}
