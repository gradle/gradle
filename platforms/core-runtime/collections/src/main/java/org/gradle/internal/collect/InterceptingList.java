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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.UnaryOperator;

/**
 * A generic {@link List} decorator over {@link InterceptingCollection}, additionally notifying the
 * positional mutators and mutations reached through {@link #listIterator()} and {@link #subList}.
 */
public class InterceptingList<E> extends InterceptingCollection<E, List<E>> implements List<E> {

    public InterceptingList(List<E> delegate, Interceptor interceptor) {
        super(delegate, interceptor);
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
    public E set(int index, E element) {
        interceptor.onMutate("set(int, Object)");
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        interceptor.onMutate("add(int, Object)");
        delegate.add(index, element);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        interceptor.onMutate("addAll(int, Collection)");
        return delegate.addAll(index, c);
    }

    @Override
    public E remove(int index) {
        interceptor.onMutate("remove(int)");
        return delegate.remove(index);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        interceptor.onMutate("replaceAll(UnaryOperator)");
        delegate.replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super E> c) {
        interceptor.onMutate("sort(Comparator)");
        delegate.sort(c);
    }

    @Override
    public ListIterator<E> listIterator() {
        return interceptingListIterator(delegate.listIterator());
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return interceptingListIterator(delegate.listIterator(index));
    }

    private ListIterator<E> interceptingListIterator(ListIterator<E> it) {
        return new ListIterator<E>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                return it.next();
            }

            @Override
            public boolean hasPrevious() {
                return it.hasPrevious();
            }

            @Override
            public E previous() {
                return it.previous();
            }

            @Override
            public int nextIndex() {
                return it.nextIndex();
            }

            @Override
            public int previousIndex() {
                return it.previousIndex();
            }

            @Override
            public void remove() {
                interceptor.onMutate("listIterator().remove()");
                it.remove();
            }

            @Override
            public void set(E e) {
                interceptor.onMutate("listIterator().set(Object)");
                it.set(e);
            }

            @Override
            public void add(E e) {
                interceptor.onMutate("listIterator().add(Object)");
                it.add(e);
            }
        };
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        // A sublist is a live view; wrap it so its mutators notify too, with the path prefixed.
        return new InterceptingList<>(delegate.subList(fromIndex, toIndex), sig -> interceptor.onMutate("subList()." + sig));
    }
}
