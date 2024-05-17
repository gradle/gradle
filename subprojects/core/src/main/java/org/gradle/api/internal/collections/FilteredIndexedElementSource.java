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

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

// TODO make this work with pending elements
public class FilteredIndexedElementSource<T, S extends T> extends FilteredElementSource<T, S> implements IndexedElementSource<S> {
    public FilteredIndexedElementSource(ElementSource<T> collection, CollectionFilter<S> filter) {
        super(collection, filter);
    }

    @Override
    public void add(int index, S element) {
        throw new UnsupportedOperationException(String.format("Cannot add '%s' to '%s' as it is a filtered collection", element, this));
    }

    @Override
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

    @Override
    public S set(int index, S element) {
        throw new UnsupportedOperationException(String.format("Cannot set '%s' in '%s' as it is a filtered collection", element, this));
    }

    @Override
    public S remove(int index) {
        throw new UnsupportedOperationException(String.format("Cannot remove element from '%s' as it is a filtered collection", this));
    }

    @Override
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

    @Override
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

    @Override
    public ListIterator<S> listIterator() {
        return new FilteredListIterator<S>(iterator());
    }

    @Override
    public ListIterator<S> listIterator(int index) {
        ListIterator<S> iterator = listIterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator;
    }

    @Override
    public List<S> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private static class FilteredListIterator<T> implements ListIterator<T> {
        private final Iterator<T> iterator;
        private int nextIndex;

        FilteredListIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        @Override
        public T next() {
            nextIndex++;
            return iterator.next();
        }

        @Override
        public T previous() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        @Override
        public int nextIndex() {
            return nextIndex;
        }

        @Override
        public int previousIndex() {
            return nextIndex - 1;
        }

        @Override
        public void add(T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(T t) {
            throw new UnsupportedOperationException();
        }
    }
}
