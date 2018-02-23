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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class FilteredList<T, S extends T> extends FilteredCollection<T, S> implements List<S> {
    public FilteredList(Collection<T> collection, CollectionFilter<S> filter) {
        super(collection, filter);
    }

    public void add(int index, S element) {
        throw new UnsupportedOperationException(String.format("Cannot add '%s' to '%s' as it is a filtered collection", element, this));
    }

    public boolean addAll(int index, Collection<? extends S> c) {
        throw new UnsupportedOperationException(String.format("Cannot add all from '%s' to '%s' as it is a filtered collection", c, this));
    }

    public S get(int index) {
        int nextIndex = 0;
        for (T t : collection) {
            S s = filter.filter(t);
            if (s != null) {
                if (nextIndex == index) {
                    return s;
                }
                nextIndex++;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    public S set(int index, S element) {
        throw new UnsupportedOperationException(String.format("Cannot set '%s' in '%s' as it is a filtered collection", element, this));
    }

    public S remove(int index) {
        throw new UnsupportedOperationException(String.format("Cannot remove element from '%s' as it is a filtered collection", this));
    }

    public int indexOf(Object o) {
        int nextIndex = 0;
        for (T t : collection) {
            S s = filter.filter(t);
            if (s != null) {
                if (s.equals(o)) {
                    return nextIndex;
                }
                nextIndex++;
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        int nextIndex = 0;
        int lastMatch = -1;
        for (T t : collection) {
            S s = filter.filter(t);
            if (s != null) {
                if (s.equals(o)) {
                    lastMatch = nextIndex;
                }
                nextIndex++;
            }
        }
        return lastMatch;
    }

    public ListIterator<S> listIterator() {
        return new FilteredListIterator<S>(iterator());
    }

    public ListIterator<S> listIterator(int index) {
        ListIterator<S> iterator = listIterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator;
    }

    public List<S> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private static class FilteredListIterator<T> implements ListIterator<T> {
        private final Iterator<T> iterator;
        private int nextIndex;

        public FilteredListIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public boolean hasPrevious() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        public T next() {
            nextIndex++;
            return iterator.next();
        }

        public T previous() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void add(T t) {
            throw new UnsupportedOperationException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void set(T t) {
            throw new UnsupportedOperationException();
        }
    }
}
