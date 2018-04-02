/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.collections;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

public class SortedSetElementSource<T> implements ElementSource<T> {
    private final TreeSet<T> values;

    public SortedSetElementSource(Comparator<T> comparator) {
        this.values = new TreeSet<T>(comparator);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return values.isEmpty();
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public int estimatedSize() {
        return values.size();
    }

    @Override
    public Iterator<T> iterator() {
        return values.iterator();
    }

    @Override
    public boolean contains(Object element) {
        return values.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        return values.containsAll(elements);
    }

    @Override
    public boolean add(T element) {
        return values.add(element);
    }

    @Override
    public boolean remove(Object o) {
        return values.remove(o);
    }

    @Override
    public void clear() {
        values.clear();
    }
}
