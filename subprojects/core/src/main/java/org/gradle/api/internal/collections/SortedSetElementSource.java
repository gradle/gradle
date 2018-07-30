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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

public class SortedSetElementSource<T> implements ElementSource<T> {
    private final TreeSet<T> values;
    private final PendingSource<T> pending = new DefaultPendingSource<T>();

    public SortedSetElementSource(Comparator<T> comparator) {
        this.values = new TreeSet<T>(comparator);
    }

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
    public boolean add(T element) {
        return values.add(element);
    }

    @Override
    public boolean addRealized(T element) {
        return values.add(element);
    }

    @Override
    public boolean remove(Object o) {
        return values.remove(o);
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
    public boolean addPending(ProviderInternal<? extends T> provider) {
        return pending.addPending(provider);
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        return pending.removePending(provider);
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return pending.addPendingCollection(provider);
    }

    @Override
    public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return pending.removePendingCollection(provider);
    }

    @Override
    public void onRealize(Action<T> action) {
        pending.onRealize(action);
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {
        pending.realizeExternal(provider);
    }
}
