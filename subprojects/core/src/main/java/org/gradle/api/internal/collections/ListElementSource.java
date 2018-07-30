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

import com.google.common.collect.Lists;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class ListElementSource<T> extends AbstractIterationOrderRetainingElementSource<T> implements IndexedElementSource<T> {

    private final Spec<ValuePointer<T>> alwaysAccept = new Spec<ValuePointer<T>>() {
        @Override
        public boolean isSatisfiedBy(ValuePointer<T> pointer) {
            return true;
        }
    };

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return listIterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return listIterator();
    }

    @Override
    public ListIterator<T> listIterator() {
        return new RealizedElementListIterator<T>(getInserted(), alwaysAccept);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return Lists.newArrayList(listIterator()).listIterator(index);
    }

    @Override
    public List<? extends T> subList(int fromIndex, int toIndex) {
        return Lists.newArrayList(listIterator()).subList(fromIndex, toIndex);
    }

    private List<T> asList() {
        return Lists.newArrayList(listIterator());
    }

    @Override
    public T get(int index) {
        return asList().get(index);
    }

    @Override
    public int indexOf(Object o) {
        return asList().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return asList().lastIndexOf(o);
    }

    @Override
    public boolean add(T element) {
        return getInserted().add(new Element<T>(element));
    }

    @Override
    public boolean addRealized(T value) {
        return true;
    }

    @Override
    public boolean addPending(ProviderInternal<? extends T> provider) {
        return getInserted().add(cachingElement(provider));
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return getInserted().add(cachingElement(provider));
    }

    private ListIterator<T> iteratorAt(int index) {
        ListIterator<T> iterator = listIterator();
        while(iterator.previousIndex() < index && iterator.hasNext()) {
            iterator.next();
        }
        return iterator;
    }

    @Override
    public void add(int index, T element) {
        ListIterator<T> iterator = iteratorAt(index - 1);
        if (iterator.nextIndex() == index) {
            iterator.add(element);
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public T set(int index, T element) {
        ListIterator<T> iterator = iteratorAt(index - 1);
        if (!iterator.hasNext()) {
            throw new IndexOutOfBoundsException();
        }
        T previous = iterator.next();
        iterator.set(element);
        return previous;
    }

    @Override
    public T remove(int index) {
        ListIterator<T> iterator = iteratorAt(index - 1);
        if (!iterator.hasNext()) {
            throw new IndexOutOfBoundsException();
        }
        T previous = iterator.next();
        iterator.remove();
        return previous;
    }

    // TODO Check for comodification with the ElementSource
    private static class RealizedElementListIterator<T> extends RealizedElementCollectionIterator<T> implements ListIterator<T> {
        T previous;
        int listNextIndex = 0;
        int listPreviousIndex = -1;

        RealizedElementListIterator(List<Element<T>> backingList, Spec<ValuePointer<T>> acceptanceSpec) {
            super(backingList, acceptanceSpec);
        }

        @Override
        public boolean hasPrevious() {
            return previous != null;
        }

        private void updatePrevious() {
            int i = previousIndex;
            while (i >= 0) {
                Element<T> candidate = backingList.get(i);
                if (candidate.isRealized()) {
                    List<T> collected = collectFrom(candidate);
                    if (previousSubIndex == -1) {
                        previousSubIndex = collected.size();
                    }
                    int j = previousSubIndex - 1;
                    while (j >= 0) {
                        T value = collected.get(j);
                        if (acceptanceSpec.isSatisfiedBy(new ValuePointer<T>(candidate, j))) {
                            previousIndex = i;
                            previousSubIndex = j;
                            previous = value;
                            return;
                        }
                        j--;
                    }
                    previousSubIndex = -1;
                }
                i--;
            }
            previousIndex = -1;
            previous = null;
        }

        @Override
        public T next() {
            T value = super.next();
            previous = collectFrom(backingList.get(previousIndex)).get(previousSubIndex);
            listNextIndex++;
            listPreviousIndex++;
            return value;
        }

        @Override
        public T previous() {
            if (previous == null) {
                throw new NoSuchElementException();
            }
            nextIndex = previousIndex;
            nextSubIndex = previousSubIndex;
            next = previous;
            updatePrevious();
            listNextIndex--;
            listPreviousIndex--;
            return next;
        }

        @Override
        public int nextIndex() {
            return listNextIndex;
        }

        @Override
        public int previousIndex() {
            return listPreviousIndex;
        }

        @Override
        public void set(T t) {
            if (previousIndex < 0) {
                throw new IllegalStateException();
            }
            backingList.set(previousIndex, new Element<T>(t));
        }

        @Override
        public void add(T t) {
            Element<T> element = new Element<T>(t);
            backingList.add(nextIndex, element);
            nextIndex++;
            previous = collectFrom(element).get(0);
            previousIndex = nextIndex;
            previousSubIndex = 0;
        }

        @Override
        public void remove() {
            super.remove();
            previous = null;
        }
    }
}
