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

import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import org.gradle.api.Action;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.ProviderInternal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class IterationOrderRetainingSetElementSource<T> implements ElementSource<T> {
    // This set represents the elements which have been realized and "added" to the store.  This may
    // or may not be in the order that elements are inserted.  We track this to know which realized
    // elements have already had rules fired against them.
    private final Set<T> added = new HashSet<T>();

    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final List<RealizableProvider<T>> inserted = new ArrayList<RealizableProvider<T>>();

    private Action<ProviderInternal<? extends T>> realizeAction;

    @Override
    public boolean isEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public int size() {
        return inserted.size();
    }

    @Override
    public int estimatedSize() {
        return inserted.size();
    }

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new RealizedElementSetIterator<T>(inserted.listIterator());
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new RealizedElementSetIterator<T>(inserted.listIterator());
    }

    @Override
    public boolean contains(Object element) {
        return Iterators.contains(iterator(), element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        for (Object e : elements) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean add(T element) {
        if (added.add(element)) {
            if (!Iterators.contains(iteratorNoFlush(), element)) {
                inserted.add(new CachingProvider(element));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        Iterator<RealizableProvider<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            RealizableProvider<? extends T> provider = iterator.next();
            if (provider.isRealized() && provider.get().equals(o)) {
                iterator.remove();
                return added.remove(o);
            }
        }
        return false;
    }

    @Override
    public void clear() {
        inserted.clear();
        added.clear();
    }

    @Override
    public void realizePending() {
        for (RealizableProvider<T> provider : inserted) {
            if (!provider.isRealized()) {
                provider.realize();
            }
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        for (RealizableProvider<T> provider : inserted) {
            if (!provider.isRealized() && (provider.getDelegateType() == null || type.isAssignableFrom(provider.getDelegateType()))) {
                provider.realize();
            }
        }
    }

    @Override
    public boolean addPending(ProviderInternal<? extends T> provider) {
        inserted.add(new CachingProvider(provider));
        return true;
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        Iterator<RealizableProvider<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            RealizableProvider<T> next = iterator.next();
            if (next.caches(provider)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRealize(final Action<ProviderInternal<? extends T>> action) {
        this.realizeAction = action;
    }

    private static class RealizedElementSetIterator<T> implements Iterator<T> {
        private final ListIterator<RealizableProvider<T>> iterator;
        private T next;
        private Set<T> values = new HashSet<T>();
        private int lastRealizedIndex = -1;

        RealizedElementSetIterator(ListIterator<RealizableProvider<T>> iterator) {
            this.iterator = iterator;
            this.next = getNext();
        }

        T getNext() {
            while (iterator.hasNext()) {
                int nextIndex = iterator.nextIndex();
                RealizableProvider<T> nextCandidate = iterator.next();
                if (nextCandidate.isRealized()) {
                    T value = nextCandidate.get();
                    if (values.add(value)) {
                        lastRealizedIndex = nextIndex;
                        return value;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next != null) {
                T thisNext = next;
                next = getNext();
                return thisNext;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            if (lastRealizedIndex > -1) {
                while (iterator.previousIndex() >= lastRealizedIndex) {
                    iterator.previous();
                }
                iterator.remove();
            } else {
                throw new IllegalStateException();
            }
        }
    }

    private interface RealizableProvider<T> extends ProviderInternal<T> {
        boolean isRealized();
        boolean caches(ProviderInternal<? extends T> provider);
        void realize();
        Class<? extends T> getDelegateType();
    }

    private class CachingProvider extends AbstractProvider<T> implements RealizableProvider<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;

        CachingProvider(final ProviderInternal<? extends T> delegate) {
            this.delegate = delegate;
        }

        CachingProvider(T value) {
            this.value = value;
            this.realized = true;
            this.delegate = null;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        public Class<? extends T> getDelegateType() {
            if (delegate != null) {
                return delegate.getType();
            } else {
                return null;
            }
        }

        @Override
        public boolean isRealized() {
            return realized;
        }

        @Override
        public void realize() {
            if (value == null && delegate != null) {
                value = delegate.get();
                realized = true;
                realizeAction.execute(this);
            }
        }

        @Nullable
        @Override
        public T getOrNull() {
            if (!realized) {
                realize();
            }
            return value;
        }

        @Override
        public boolean caches(ProviderInternal<? extends T> provider) {
            return Objects.equal(delegate, provider);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachingProvider that = (CachingProvider) o;
            return Objects.equal(delegate, that.delegate) &&
                Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate, value);
        }
    }
}
