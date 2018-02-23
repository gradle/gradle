/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DelegatingDomainObjectSet<T> implements WithEstimatedSize {

    private final Spec<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private final Spec<T> notInSpec = new ItemNotInCompositeSpec();
    private final DefaultDomainObjectSet<T> backingSet;

    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        //noinspection unchecked
        DefaultDomainObjectSet<T> backingSet = new DefaultDomainObjectSet<T>(type, new DomainObjectCompositeCollection());
        CompositeDomainObjectSet<T> out = new CompositeDomainObjectSet<T>(backingSet);
        for (DomainObjectCollection<? extends T> c : collections) {
            out.addCollection(c);
        }
        return out;
    }

    CompositeDomainObjectSet(DefaultDomainObjectSet<T> backingSet) {
        super(backingSet);
        this.backingSet = backingSet; //TODO SF try avoiding keeping this state here
    }

    public class ItemIsUniqueInCompositeSpec implements Spec<T> {
        public boolean isSatisfiedBy(T element) {
            int matches = 0;
            for (Object collection : getStore().store) {
                if (((Collection) collection).contains(element)) {
                    if (++matches > 1) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public class ItemNotInCompositeSpec implements Spec<T> {
        public boolean isSatisfiedBy(T element) {
            return !getStore().contains(element);
        }
    }

    @SuppressWarnings("unchecked")
    protected DomainObjectCompositeCollection getStore() {
        return (DomainObjectCompositeCollection) this.backingSet.getStore();
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().containsCollection(collection)) {
            getStore().addComposited(collection);
            collection.all(backingSet.getEventRegister().getAddAction());
            collection.whenObjectRemoved(backingSet.getEventRegister().getRemoveAction());
        }
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        Action<? super T> action = this.backingSet.getEventRegister().getRemoveAction();
        for (T item : collection) {
            action.execute(item);
        }
    }

    @SuppressWarnings({"NullableProblems", "unchecked"})
    @Override
    public Iterator<T> iterator() {
        DomainObjectCompositeCollection store = getStore();
        if (store.isEmpty()) {
            return Iterators.emptyIterator();
        }
        return SetIterator.of(store);

    }

    @SuppressWarnings("unchecked")
    /**
     * This method is expensive. Avoid calling it if possible. If all you need is a rough
     * estimate, call {@link #estimatedSize()} instead.
     */
    public int size() {
        DomainObjectCompositeCollection store = getStore();
        if (store.isEmpty()) {
            return 0;
        }
        Set<T> tmp = Sets.newHashSetWithExpectedSize(estimatedSize());
        tmp.addAll(store);
        return tmp.size();
    }

    @Override
    public int estimatedSize() {
        return getStore().estimatedSize();
    }

    public void all(Action<? super T> action) {
        //calling overloaded method with extra behavior:
        whenObjectAdded(action);

        for (T t : this) {
            action.execute(t);
        }
    }

    private final static class DomainObjectCompositeCollection<T> implements Collection<T>, WithEstimatedSize {

        private final List<DomainObjectCollection<? extends T>> store = Lists.newLinkedList();

        public boolean containsCollection(DomainObjectCollection<? extends T> collection) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts == collection) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int size() {
            int size = 0;
            for (DomainObjectCollection<? extends T> ts : store) {
                size += ts.size();
            }
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (!ts.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean contains(Object o) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts.contains(o)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            if (store.isEmpty()) {
                return Iterators.emptyIterator();
            }
            if (store.size() == 1) {
                return (Iterator<T>) store.get(0).iterator();
            }
            Iterator[] iterators = new Iterator[store.size()];
            int i=0;
            for (DomainObjectCollection<? extends T> ts : store) {
                iterators[i++] = ts.iterator();
            }
            return Iterators.<T>concat(iterators);
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> V[] toArray(V[] a) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public void addComposited(DomainObjectCollection<? extends T> collection) {
            this.store.add(collection);
        }

        public void removeComposited(DomainObjectCollection<? extends T> collection) {
            Iterator<DomainObjectCollection<? extends T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                DomainObjectCollection<? extends T> next = iterator.next();
                if (next == collection) {
                    iterator.remove();
                    break;
                }
            }
        }

        @Override
        public int estimatedSize() {
            int size = 0;
            for (DomainObjectCollection<? extends T> ts : store) {
                size += Estimates.estimateSizeOf(ts);
            }
            return size;
        }
    }
}
