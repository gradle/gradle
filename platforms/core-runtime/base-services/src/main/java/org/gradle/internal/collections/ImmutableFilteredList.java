/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.collections;

import org.gradle.api.specs.Spec;

import java.util.AbstractList;
import java.util.BitSet;
import java.util.List;

/**
 * An immutable list which wraps another source list by applying some static filter to it.
 * This list is optimized to avoid copying the source list when performing operations which filter
 * items from the source list. Instead, the source list is kept intact and the only additional state is
 * to keep track of which items are filtered out of the source list.
 */
public class ImmutableFilteredList<T> extends AbstractList<T> {

    private final List<T> source;
    private final BitSet filter;

    private ImmutableFilteredList(List<T> source, BitSet filter) {
        this.source = source;
        this.filter = filter;
    }

    /**
     * Creates an {@code ImmutableFilteredList} which contains all the elements of {@code source}.
     *
     * This method assumes that {@code source} will not be mutated after this method is called.
     */
    public static <T> ImmutableFilteredList<T> allOf(List<T> source) {
        BitSet filter = new BitSet(source.size());
        filter.set(0, source.size());
        return new ImmutableFilteredList<T>(source, filter);
    }

    /**
     * Given an {@code other} {@code ImmutableFilteredList}, return a new list which contains all items in
     * this list, except with the item at the specified {@code index} in {@code other} excluded.
     * <p>
     * This method assumes that this list and {@code other} share the same source list.
     */
    public ImmutableFilteredList<T> withoutIndexFrom(int index, ImmutableFilteredList<T> other) {
        assert other.source == source;

        BitSet newFilter = new BitSet(source.size());
        newFilter.or(filter);
        newFilter.clear(other.getSourceIndex(index));
        return new ImmutableFilteredList<T>(source, newFilter);
    }

    /**
     * Returns a new list which contains all items in this list which match the provided {@code matcher}.
     */
    public ImmutableFilteredList<T> matching(Spec<T> matcher) {
        BitSet newFilter = new BitSet(source.size());
        for (int actual = filter.nextSetBit(0); actual >= 0; actual = filter.nextSetBit(actual + 1)) {
            if (matcher.isSatisfiedBy(source.get(actual))) {
                newFilter.set(actual);
            }
        }
        return new ImmutableFilteredList<T>(source, newFilter);
    }

    @Override
    public T get(int index) {
        return source.get(getSourceIndex(index));
    }

    @Override
    public int size() {
        return filter.cardinality();
    }

    /**
     * Given an index in this list, get the corresponding index in the source list.
     */
    private int getSourceIndex(int index) {
        int actual = filter.nextSetBit(0);
        for (int i = 0; i < index; i++) {
            actual = filter.nextSetBit(actual + 1);
        }
        return actual;
    }
}
