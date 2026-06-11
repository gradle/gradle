/*
 * Copyright 2026 the original author or authors.
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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A {@link List} wrapper that notifies a callback when any mutating method is called,
 * including mutation through {@link #iterator()}, {@link #listIterator()} and {@link #subList(int, int)}.
 */
public class MutationNotifyingList<E> extends MutationNotifyingCollection<E, List<E>> implements List<E> {

    public MutationNotifyingList(List<E> delegate, Consumer<String> onMutation) {
        super(delegate, onMutation);
    }

    @Override
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return new MutationNotifyingListIterator<E>(delegate.listIterator(), onMutation);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new MutationNotifyingListIterator<E>(delegate.listIterator(index), onMutation);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        // A sublist is a live view; wrap recursively so its mutators, iterators and nested sublists notify too.
        return new MutationNotifyingList<E>(delegate.subList(fromIndex, toIndex), name -> onMutation.accept("subList()." + name));
    }

    @Override
    public void add(int index, E element) {
        onMutation.accept("add(int, Object)");
        delegate.add(index, element);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        onMutation.accept("addAll(int, Collection)");
        return delegate.addAll(index, c);
    }

    @Override
    public E set(int index, E element) {
        onMutation.accept("set(int, Object)");
        return delegate.set(index, element);
    }

    @Override
    public E remove(int index) {
        onMutation.accept("remove(int)");
        return delegate.remove(index);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        onMutation.accept("replaceAll(UnaryOperator)");
        delegate.replaceAll(operator);
    }

    @Override
    public void sort(@Nullable Comparator<? super E> c) {
        onMutation.accept("sort(Comparator)");
        delegate.sort(c);
    }
}
