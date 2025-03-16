/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

import javax.annotation.CheckReturnValue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * A special append-only list used in collection properties implementations.
 * Pretends to be an immutable data structure but isn't actually thread-safe.
 * <p>
 * Appending an element to each {@code AppendOnceList} with {@link #plus(Object)} can only be done once per list, because the produced lists share the internal buffer.
 * Chained calls are ok: {@code new AppendOnceList.of("a").plus("b").plus("c")}.
 * <p>
 * Unlike the {@code PersistentList}, this list appends elements to the back.
 *
 * @param <E> the type of elements
 */
class AppendOnceList<E> implements Iterable<E> {
    // The potentially shared buffer for list items.
    private final ArrayList<E> buffer;
    // The number of the items in _this_ list. Note that buffer.size() >= this.size, because appends share the buffer.
    private final int size;

    /**
     * Creates an empty AppendOnceList.
     */
    private AppendOnceList() {
        buffer = new ArrayList<>();
        size = 0;
    }

    /**
     * An internal constructor used by {@link #toAppendOnceList()} collector. Takes ownership of the {@code items}.
     *
     * @param items the items to use
     */
    private AppendOnceList(@SuppressWarnings("NonApiType") ArrayList<E> items) {
        buffer = items;
        size = items.size();
    }

    /**
     * The internal appending constructor. Uses the buffer from parent.
     *
     * @param parent the list to append to
     * @param element the element to append
     */
    private AppendOnceList(AppendOnceList<E> parent, E element) {
        Preconditions.checkState(parent.buffer.size() == parent.size, "Can only append an element to the list once");
        buffer = parent.buffer;
        buffer.add(element);
        size = parent.size + 1;
    }

    /**
     * Creates an empty AppendOnceList. The list can be appended to.
     *
     * @return the empty list
     */
    public static <E> AppendOnceList<E> of() {
        return new AppendOnceList<>();
    }

    /**
     * Creates a list with a single element. The list can be appended to.
     *
     * @param value the element
     * @param <E> the type of elements
     * @return the list with a single element
     */
    public static <E> AppendOnceList<E> of(E value) {
        return new AppendOnceList<E>().plus(value);
    }

    /**
     * Creates a new list with the elements of this list and the given element appended to the end. Can only be called once for a list instance.
     *
     * @param element the element to append
     * @return the new list
     * @throws IllegalStateException if another element was appended to this list already.
     */
    @CheckReturnValue
    public AppendOnceList<E> plus(E element) {
        return new AppendOnceList<>(this, element);
    }

    private List<E> getItems() {
        // We cannot cache the list, because it throws ConcurrentModificationException upon changes not made through it.
        return buffer.subList(0, size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned iterator doesn't support removals.
     */
    @Override
    public Iterator<E> iterator() {
        return Iterators.unmodifiableIterator(getItems().iterator());
    }

    @Override
    public Spliterator<E> spliterator() {
        return getItems().spliterator();
    }

    /**
     * Returns a stream of elements.
     *
     * @return the stream of elements
     */
    public Stream<E> stream() {
        return getItems().stream();
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Creates a {@link Collector} to collect a stream into the AppendOnceList. Elements can be appended to the collected list.
     *
     * @param <E> the type of elements
     * @return the collector
     */
    public static <E> Collector<E, ?, AppendOnceList<E>> toAppendOnceList() {
        return Collector.<E, ArrayList<E>, AppendOnceList<E>>of(
            ArrayList::new,
            ArrayList::add,
            (l, r) -> {
                l.addAll(r);
                return l;
            }, AppendOnceList::new);
    }
}
