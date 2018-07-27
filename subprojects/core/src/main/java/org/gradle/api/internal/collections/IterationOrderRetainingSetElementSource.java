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
import org.gradle.api.internal.provider.ProviderInternal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class IterationOrderRetainingSetElementSource<T> implements ElementSource<T> {
    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final List<Element<T>> inserted = new ArrayList<Element<T>>();

    private Action<T> realizeAction;

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
        if (!Iterators.contains(iteratorNoFlush(), element)) {
            inserted.add(new CachingElement(element));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addRealized(T element) {
        return true;
    }

    @Override
    public boolean remove(Object o) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<? extends T> provider = iterator.next();
            if (provider.isRealized() && provider.getValue().equals(o)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        inserted.clear();
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {

    }

    @Override
    public void realizePending() {
        for (Element<T> provider : inserted) {
            if (!provider.isRealized()) {
                provider.realize();
            }
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        for (Element<T> provider : inserted) {
            if (!provider.isRealized() && (provider.getType() == null || type.isAssignableFrom(provider.getType()))) {
                provider.realize();
            }
        }
    }

    @Override
    public boolean addPending(ProviderInternal<? extends T> provider) {
        CachingElement element = new CachingElement(provider);
        if (!inserted.contains(element)) {
            inserted.add(element);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<T> next = iterator.next();
            if (next.caches(provider)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRealize(final Action<T> action) {
        this.realizeAction = action;
    }

    protected interface Element<T> {
        boolean isRealized();
        boolean caches(ProviderInternal<? extends T> provider);
        void realize();
        Class<? extends T> getType();
        T getValue();
    }

    protected class CachingElement implements Element<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;

        CachingElement(final ProviderInternal<? extends T> delegate) {
            this.delegate = delegate;
        }

        CachingElement(T value) {
            this.value = value;
            this.realized = true;
            this.delegate = null;
        }

        public Class<? extends T> getType() {
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
                if (realizeAction != null) {
                    realizeAction.execute(value);
                }
            }
        }

        @Nullable
        @Override
        public T getValue() {
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
            IterationOrderRetainingSetElementSource.CachingElement that = (IterationOrderRetainingSetElementSource.CachingElement) o;
            return Objects.equal(delegate, that.delegate) &&
                Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate, value);
        }
    }

    protected static class RealizedElementSetIterator<T> implements Iterator<T> {
        private final ListIterator<Element<T>> iterator;
        private final Collection<T> values = new HashSet<T>();
        private T next;
        private int lastRealizedIndex = -1;

        RealizedElementSetIterator(ListIterator<IterationOrderRetainingSetElementSource.Element<T>> iterator) {
            this.iterator = iterator;
            this.next = getNext();
        }

        T getNext() {
            while (iterator.hasNext()) {
                int nextIndex = iterator.nextIndex();
                Element<T> nextCandidate = iterator.next();
                if (nextCandidate.isRealized()) {
                    T value = nextCandidate.getValue();
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
}
