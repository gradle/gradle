/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.WithEstimatedSize;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.Cast;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class FilteredElementSource<T, S extends T> implements ElementSource<S> {
    protected final ElementSource<T> collection;
    protected final CollectionFilter<S> filter;

    public FilteredElementSource(ElementSource<T> collection, CollectionFilter<S> filter) {
        this.collection = collection;
        this.filter = filter;
    }

    @Override
    public boolean add(S o) {
        throw new UnsupportedOperationException(String.format("Cannot add '%s' to '%s' as it is a filtered collection", o, this));
    }

    @Override
    public boolean addRealized(S o) {
        throw new UnsupportedOperationException(String.format("Cannot add '%s' to '%s' as it is a filtered collection", o, this));
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(String.format("Cannot clear '%s' as it is a filtered collection", this));
    }

    protected boolean accept(Object o) {
        return filter.filter(o) != null;
    }

    @Override
    public boolean contains(Object o) {
        return collection.contains(o) && accept(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (collection.containsAll(c)) {
            for (Object o : c) {
                if (!accept(o)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return collection.constantTimeIsEmpty();
    }

    @Override
    public boolean isEmpty() {
        if (collection.isEmpty()) {
            return true;
        } else {
            for (T o : this) {
                if (accept(o)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int estimatedSize() {
        return collection.estimatedSize();
    }

    @Override
    public MutationGuard getMutationGuard() {
        return collection.getMutationGuard();
    }

    private static class FilteringIterator<T, S extends T> implements Iterator<S>, WithEstimatedSize {
        private final CollectionFilter<S> filter;
        private final Iterator<T> iterator;
        private final int estimatedSize;

        private S next;

        FilteringIterator(ElementSource<T> collection, CollectionFilter<S> filter) {
            this.iterator = collection.iteratorNoFlush();
            this.filter = filter;
            this.estimatedSize = collection.estimatedSize();
            this.next = findNext();
        }

        private S findNext() {
            while (iterator.hasNext()) {
                T potentialNext = iterator.next();
                S filtered = filter.filter(potentialNext);
                if (filtered != null) {
                    return filtered;
                }
            }

            return null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public S next() {
            if (next != null) {
                S thisNext = next;
                next = findNext();
                return thisNext;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("This iterator does not support removal");
        }

        @Override
        public int estimatedSize() {
            return estimatedSize;
        }
    }

    @Override
    public Iterator<S> iterator() {
        collection.realizePending(filter.getType());
        return new FilteringIterator<T, S>(collection, filter);
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException(String.format("Cannot remove '%s' from '%s' as it is a filtered collection", o, this));
    }

    @Override
    public int size() {
        int i = 0;
        // NOTE: There isn't much we can do about collection.matching { } filters as the spec requires a realized element, unless make major changes
        for (T o : this) {
            if (accept(o)) {
                ++i;
            }
        }
        return i;
    }

    @Override
    public Iterator<S> iteratorNoFlush() {
        return new FilteringIterator<T, S>(collection, filter);
    }

    @Override
    public void realizePending() {
        realizePending(filter.getType());
    }

    @Override
    public void realizePending(Class<?> type) {
        collection.realizePending(type);
    }

    @Override
    public boolean addPending(ProviderInternal<? extends S> provider) {
        return collection.addPending(provider);
    }

    @Override
    public boolean removePending(ProviderInternal<? extends S> provider) {
        return collection.removePending(provider);
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<S, ? extends Iterable<S>> provider) {
        CollectionProviderInternal<T, ? extends Iterable<T>> providerOfT = Cast.uncheckedCast(provider);
        return collection.addPendingCollection(providerOfT);
    }

    @Override
    public boolean removePendingCollection(CollectionProviderInternal<S, ? extends Iterable<S>> provider) {
        CollectionProviderInternal<T, ? extends Iterable<T>> providerOfT = Cast.uncheckedCast(provider);
        return collection.removePendingCollection(providerOfT);
    }

    @Override
    public void onRealize(Action<S> action) { }

    @Override
    public void realizeExternal(ProviderInternal<? extends S> provider) {
        collection.realizeExternal(provider);
    }
}
