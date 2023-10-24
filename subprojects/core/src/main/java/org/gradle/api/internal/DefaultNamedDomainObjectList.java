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
package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.Namer;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.FilteredIndexedElementSource;
import org.gradle.api.internal.collections.IndexedElementSource;
import org.gradle.api.internal.collections.ListElementSource;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class DefaultNamedDomainObjectList<T> extends DefaultNamedDomainObjectCollection<T> implements NamedDomainObjectList<T> {
    public DefaultNamedDomainObjectList(DefaultNamedDomainObjectList<? super T> objects, CollectionFilter<T> filter, Instantiator instantiator, Namer<? super T> namer) {
        super(objects, filter, instantiator, namer);
    }

    public DefaultNamedDomainObjectList(Class<T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator decorator) {
        super(type, new ListElementSource<T>(), instantiator, namer, decorator);
    }

    private DefaultNamedDomainObjectList(DefaultNamedDomainObjectList<? super T> objects, @Nullable Spec<String> nameFilter, CollectionFilter<T> elementFilter, Instantiator instantiator, Namer<? super T> namer) {
        super(objects, nameFilter, elementFilter, instantiator, namer);
    }

    @Override
    public void add(int index, T element) {
        assertMutable("add(int, T)");
        assertCanAdd(element);
        getStore().add(index, element);
        didAdd(element);
        getEventRegister().fireObjectAdded(element);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        assertMutable("addAll(int, Collection)");
        boolean changed = false;
        int current = index;
        for (T t : c) {
            if (!hasWithName(getNamer().determineName(t))) {
                getStore().add(current, t);
                didAdd(t);
                getEventRegister().fireObjectAdded(t);
                changed = true;
                current++;
            }
        }
        return changed;
    }

    @Override
    protected IndexedElementSource<T> getStore() {
        return (IndexedElementSource<T>) super.getStore();
    }

    @Override
    public T get(int index) {
        return getStore().get(index);
    }

    @Override
    public T set(int index, T element) {
        assertMutable("set(int, T)");
        assertCanAdd(element);
        T oldElement = getStore().set(index, element);
        if (oldElement != null) {
            didRemove(oldElement);
        }
        getEventRegister().fireObjectRemoved(oldElement);
        didAdd(element);
        getEventRegister().fireObjectAdded(element);
        return oldElement;
    }

    @Override
    public T remove(int index) {
        assertMutable("remove(int)");
        T element = getStore().remove(index);
        if (element != null) {
            didRemove(element);
        }
        getEventRegister().fireObjectRemoved(element);
        return element;
    }

    @Override
    public int indexOf(Object o) {
        return getStore().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return getStore().lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return new ListIteratorImpl(getStore().listIterator());
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return new ListIteratorImpl(getStore().listIterator(index));
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(getStore().subList(fromIndex, toIndex));
    }

    @Override
    protected <S extends T> IndexedElementSource<S> filteredStore(CollectionFilter<S> filter, ElementSource<T> elementSource) {
        return new FilteredIndexedElementSource<T, S>(elementSource, filter);
    }

    public NamedDomainObjectList<T> named(Spec<String> nameFilter) {
        Spec<T> spec = convertNameToElementFilter(nameFilter);
        return new DefaultNamedDomainObjectList<>(this, nameFilter, createFilter(spec), getInstantiator(), getNamer());
    }

    @Override
    public NamedDomainObjectList<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public NamedDomainObjectList<T> matching(Spec<? super T> spec) {
        return new DefaultNamedDomainObjectList<T>(this, createFilter(spec), getInstantiator(), getNamer());
    }

    @Override
    public <S extends T> NamedDomainObjectList<S> withType(Class<S> type) {
        return new DefaultNamedDomainObjectList<S>(this, createFilter(type), getInstantiator(), getNamer());
    }

    @Override
    public List<T> findAll(Closure cl) {
        return findAll(cl, new ArrayList<T>());
    }

    private class ListIteratorImpl implements ListIterator<T> {
        private final ListIterator<T> iterator;
        private T lastElement;

        public ListIteratorImpl(ListIterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            return iterator.hasPrevious();
        }

        @Override
        public T next() {
            lastElement = iterator.next();
            return lastElement;
        }

        @Override
        public T previous() {
            lastElement = iterator.previous();
            return lastElement;
        }

        @Override
        public int nextIndex() {
            return iterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return iterator.previousIndex();
        }

        @Override
        public void add(T t) {
            assertMutable("listIterator().add(T)");
            assertCanAdd(t);
            iterator.add(t);
            didAdd(t);
            getEventRegister().fireObjectAdded(t);
        }

        @Override
        public void remove() {
            assertMutable("listIterator().remove()");
            iterator.remove();
            didRemove(lastElement);
            getEventRegister().fireObjectRemoved(lastElement);
            lastElement = null;
        }

        @Override
        public void set(T t) {
            assertMutable("listIterator().set(T)");
            assertCanAdd(t);
            iterator.set(t);
            didRemove(lastElement);
            getEventRegister().fireObjectRemoved(lastElement);
            didAdd(t);
            getEventRegister().fireObjectAdded(t);
            lastElement = null;
        }
    }

}
