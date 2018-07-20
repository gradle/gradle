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

import org.gradle.api.Action;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class ListElementSource<T> implements IndexedElementSource<T> {
    private final List<T> values = new ArrayList<T>();
    private final PendingSource<T> pending = new DefaultPendingSource<T>();

    @Override
    public boolean isEmpty() {
        return values.isEmpty() && pending.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return values.isEmpty() && pending.isEmpty();
    }

    @Override
    public int size() {
        return values.size() + pending.size();
    }

    @Override
    public int estimatedSize() {
        return values.size() + pending.size();
    }

    @Override
    public Iterator<T> iterator() {
        pending.realizePending();
        return values.iterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return values.iterator();
    }

    @Override
    public ListIterator<T> listIterator() {
        return values.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return values.listIterator(index);
    }

    @Override
    public boolean contains(Object element) {
        pending.realizePending();
        return values.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        pending.realizePending();
        return values.containsAll(elements);
    }

    @Override
    public List<? extends T> subList(int fromIndex, int toIndex) {
        return values.subList(fromIndex, toIndex);
    }

    @Override
    public T get(int index) {
        return values.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return values.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return values.lastIndexOf(o);
    }

    @Override
    public boolean add(T element) {
        return values.add(element);
    }

    @Override
    public void add(int index, T element) {
        values.add(index, element);
    }

    @Override
    public T set(int index, T element) {
        return values.set(index, element);
    }

    @Override
    public boolean remove(Object o) {
        return values.remove(o);
    }

    @Override
    public T remove(int index) {
        return values.remove(index);
    }

    @Override
    public void clear() {
        pending.clear();
        values.clear();
    }

    @Override
    public void realizePending() {
        pending.realizePending();
    }

    @Override
    public void realizePending(Class<?> type) {
        pending.realizePending(type);
    }

    @Override
    public void addPending(ProviderInternal<? extends T> provider) {
        pending.addPending(provider);
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        pending.removePending(provider);
    }

    @Override
    public void addPendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        pending.addPendingCollection(provider);
    }

    @Override
    public void removePendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        pending.removePendingCollection(provider);
    }

    @Override
    public void onRealize(Action<CollectionProviderInternal<T, Set<T>>> action) {
        pending.onRealize(action);
    }
}
