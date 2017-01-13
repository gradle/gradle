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

import org.gradle.api.internal.WithEstimatedSize;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class FilteredCollection<T, S extends T> implements Collection<S>, WithEstimatedSize {

    protected final Collection<T> collection;
    protected final CollectionFilter<S> filter;

    public FilteredCollection(Collection<T> collection, CollectionFilter<S> filter) {
        this.collection = collection;
        this.filter = filter;
    }

    public boolean add(S o) {
        throw new UnsupportedOperationException(String.format("Cannot add '%s' to '%s' as it is a filtered collection", o, this));
    }

    public boolean addAll(Collection<? extends S> c) {
        throw new UnsupportedOperationException(String.format("Cannot add all from '%s' to '%s' as it is a filtered collection", c, this));
    }

    public void clear() {
        throw new UnsupportedOperationException(String.format("Cannot clear '%s' as it is a filtered collection", this));
    }

    protected boolean accept(Object o) {
        return filter.filter(o) != null;
    }

    public boolean contains(Object o) {
        return collection.contains(o) && accept(o);
    }

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

    // boolean equals(Object o) {
    //
    // }
    //
    // int hashCode() {
    //
    // }

    public boolean isEmpty() {
        if (collection.isEmpty()) {
            return true;
        } else {
            for (T o : collection) {
                if (accept(o)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int estimatedSize() {
        return Estimates.estimateSizeOf(collection);
    }

    protected static class FilteringIterator<T, S extends T> implements Iterator<S>, WithEstimatedSize {
        private final CollectionFilter<S> filter;
        private final Iterator<T> iterator;
        private final int estimatedSize;

        private S next;

        public FilteringIterator(Collection<T> collection, CollectionFilter<S> filter) {
            this.iterator = collection.iterator();
            this.filter = filter;
            this.estimatedSize = Estimates.estimateSizeOf(collection);
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

        public boolean hasNext() {
            return next != null;
        }

        public S next() {
            if (next != null) {
                S thisNext = next;
                next = findNext();
                return thisNext;
            } else {
                throw new NoSuchElementException();
            }
        }

        public void remove() {
            throw new UnsupportedOperationException("This iterator does not support removal");
        }

        @Override
        public int estimatedSize() {
            return estimatedSize;
        }
    }

    public Iterator<S> iterator() {
        return new FilteringIterator<T, S>(collection, filter);
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException(String.format("Cannot remove '%s' from '%s' as it is a filtered collection", o, this));
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(String.format("Cannot remove all of '%s' from '%s' as it is a filtered collection", c, this));
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(String.format("Cannot retain all of '%s' from '%s' as it is a filtered collection", c, this));
    }

    public int size() {
        int i = 0;
        for (T o : collection) {
            if (accept(o)) {
                ++i;
            }
        }
        return i;
    }

    public Object[] toArray() {
        Object[] a = new Object[size()];
        int i = 0;
        for (T o : collection) {
            if (accept(o)) {
                a[i++] = o;
            }
        }
        return a;
    }

    // TODO - a proper implementation of this
    public <T> T[] toArray(T[] a) {
        return (T[])toArray();
    }
}
