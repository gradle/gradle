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
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

abstract public class AbstractIterationOrderRetainingElementSource<T> implements ElementSource<T> {
    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final List<Element<T>> inserted = new ArrayList<Element<T>>();

    private Action<T> realizeAction;

    List<Element<T>> getInserted() {
        return inserted;
    }

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
        for (Element<T> element : inserted) {
            if (!element.isRealized()) {
                element.realize();
            }
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        for (Element<T> element : inserted) {
            if (!element.isRealized() && (element.getType() == null || type.isAssignableFrom(element.getType()))) {
                element.realize();
            }
        }
    }

    Element<T> cachingElement(ProviderInternal<? extends T> provider) {
        return new CachingElement<T>(provider, realizeAction);
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
        boolean isDuplicate();
        void setDuplicate(boolean isDuplicate);
    }

    // TODO Check for comodification with the ElementSource
    protected static class RealizedElementCollectionIterator<T> implements Iterator<T> {
        final List<Element<T>> backingList;
        final Spec<Element<T>> acceptanceSpec;
        int nextIndex = -1;
        int previousIndex = -1;
        T next;

        RealizedElementCollectionIterator(List<Element<T>> backingList, Spec<Element<T>> acceptanceSpec) {
            this.backingList = backingList;
            this.acceptanceSpec = acceptanceSpec;
            updateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        private void updateNext() {
            int i = nextIndex + 1;
            while (i < backingList.size()) {
                Element<T> candidate = backingList.get(i);
                if (candidate.isRealized() && acceptanceSpec.isSatisfiedBy(candidate)) {
                    T value = candidate.getValue();
                        nextIndex = i;
                        next = value;
                        return;
                }
                i++;
            }
            nextIndex = i;
            next = null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            T thisNext = next;
            previousIndex = nextIndex;
            updateNext();
            return thisNext;
        }

        @Override
        public void remove() {
            if (previousIndex > -1) {
                backingList.remove(previousIndex);
                previousIndex = -1;
                nextIndex--;
            } else {
                throw new IllegalStateException();
            }
        }
    }

    protected static class CachingElement<T> implements Element<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;
        private final Action<T> realizeAction;
        private boolean duplicate;

        CachingElement(final ProviderInternal<? extends T> delegate, Action<T> realizeAction) {
            this.delegate = delegate;
            this.realizeAction = realizeAction;
        }

        CachingElement(T value) {
            this.value = value;
            this.realized = true;
            this.realizeAction = null;
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
        public boolean isDuplicate() {
            return duplicate;
        }

        @Override
        public void setDuplicate(boolean isDuplicate) {
            this.duplicate = isDuplicate;
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
}
