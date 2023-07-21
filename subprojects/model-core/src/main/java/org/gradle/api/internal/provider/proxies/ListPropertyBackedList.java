/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.proxies;

import com.google.common.collect.ForwardingList;
import org.gradle.api.provider.ListProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Implementation of List, that is used for Property upgrades
 */
public class ListPropertyBackedList<E> extends ForwardingList<E> {

    private final ListProperty<E> delegate;

    public ListPropertyBackedList(ListProperty<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected @NotNull List<E> delegate() {
        return new List<E>() {
            @Override
            public int size() {
                return delegate.get().size();
            }

            @Override
            public boolean isEmpty() {
                return delegate.get().isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                return delegate.get().contains(o);
            }

            @NotNull
            @Override
            public Iterator<E> iterator() {
                // TODO: Should we support Iterator.remove()?
                return delegate.get().iterator();
            }

            @NotNull
            @Override
            public Object[] toArray() {
                return delegate.get().toArray();
            }

            @NotNull
            @Override
            public <T> T[] toArray(@NotNull T[] a) {
                return delegate.get().toArray(a);
            }

            @Override
            public boolean add(E e) {
                delegate.add(e);
                return true;
            }

            @Override
            public boolean remove(Object o) {
                List<E> set = delegate.get();
                boolean removed = set.remove(o);
                delegate.set(set);
                return removed;
            }

            @Override
            public boolean containsAll(@NotNull Collection<?> c) {
                return delegate.get().containsAll(c);
            }

            @Override
            public boolean addAll(@NotNull Collection<? extends E> c) {
                List<E> set = delegate.get();
                boolean added = set.addAll(c);
                delegate.addAll(c);
                return added;
            }

            @Override
            public boolean addAll(int index, @NotNull Collection<? extends E> c) {
                return false;
            }

            @Override
            public boolean retainAll(@NotNull Collection<?> c) {
                List<E> set = delegate.get();
                boolean removed = set.retainAll(c);
                delegate.set(set);
                return removed;
            }

            @Override
            public boolean removeAll(@NotNull Collection<?> c) {
                List<E> set = delegate.get();
                boolean removed = set.removeAll(c);
                delegate.set(set);
                return removed;
            }

            @Override
            public void clear() {
                delegate.empty();
            }

            @Override
            public E get(int index) {
                return delegate.get().get(index);
            }

            @Override
            public E set(int index, E element) {
                List<E> set = delegate.get();
                E replaced = set.set(index, element);
                delegate.set(set);
                return replaced;
            }

            @Override
            public void add(int index, E element) {
                List<E> set = delegate.get();
                set.add(index, element);
                delegate.set(set);
            }

            @Override
            public E remove(int index) {
                List<E> set = delegate.get();
                E removed = set.remove(index);
                delegate.set(set);
                return removed;
            }

            @Override
            public int indexOf(Object o) {
                return delegate.get().indexOf(o);
            }

            @Override
            public int lastIndexOf(Object o) {
                return delegate.get().lastIndexOf(o);
            }

            @NotNull
            @Override
            public ListIterator<E> listIterator() {
                // TODO: Should we support ListIterator.remove(), add(), set()?
                return delegate.get().listIterator();
            }

            @NotNull
            @Override
            public ListIterator<E> listIterator(int index) {
                // TODO: Should we support ListIterator.remove(), add(), set()?
                return delegate.get().listIterator(index);
            }

            @NotNull
            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                return delegate.get().subList(fromIndex, toIndex);
            }
        };
    }
}
