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
import org.apache.commons.collections.collection.CompositeCollection;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DelegatingDomainObjectSet<T> {

    private final Spec<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private final Spec<T> notInSpec = new ItemNotInCompositeSpec();
    private final DefaultDomainObjectSet<T> backingSet;

    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        //noinspection unchecked
        DefaultDomainObjectSet<T> backingSet = new DefaultDomainObjectSet<T>(type, new CompositeCollection());
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
            for (Object collection : getStore().getCollections()) {
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
    protected CompositeCollection getStore() {
        return (CompositeCollection) this.backingSet.getStore();
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().getCollections().contains(collection)) {
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
        CompositeCollection store = getStore();
        if (store.isEmpty()) {
            return Iterators.emptyIterator();
        }
        return new SetIterator();
    }

    @SuppressWarnings("unchecked")
    public int size() {
        CompositeCollection store = getStore();
        if (store.isEmpty()) {
            return 0;
        }
        return new HashSet<T>(store).size();
    }

    public void all(Action<? super T> action) {
        //calling overloaded method with extra behavior:
        whenObjectAdded(action);

        for (T t : this) {
            action.execute(t);
        }
    }

    private class SetIterator implements Iterator<T> {
        private final Set<T> visited = new HashSet<T>(64);
        private final Iterator<T> iterator;

        private T next;

        @SuppressWarnings("unchecked")
        public SetIterator() {
            Collection<Collection<T>> collections = getStore().getCollections();
            if (collections instanceof List && collections.size()==1) {
                // shortcut Apache Commons iterator() which creates a chaining iterator even if there's
                // only one underlying collection
                iterator = (((List<Collection<T>>) collections).get(0)).iterator();
            } else {
                iterator = getStore().iterator();
            }
            fetchNext();
        }

        private void fetchNext() {
            while (iterator.hasNext()) {
                next = iterator.next();
                if (!visited.contains(next)) {
                    visited.add(next);
                    return;
                }
            }
            next = null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            try {
                return next;
            } finally {
                fetchNext();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
