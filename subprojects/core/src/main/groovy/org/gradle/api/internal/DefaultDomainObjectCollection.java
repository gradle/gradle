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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import org.gradle.api.internal.collections.FilteredCollection;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.CollectionEventRegister;

import java.util.*;

public class DefaultDomainObjectCollection<T> implements DomainObjectCollection<T> {

    private final Class<T> type;
    private final CollectionEventRegister<T> eventRegister;
    private final Collection<T> store;

    public DefaultDomainObjectCollection(Class<T> type, Collection<T> store) {
        this(type, store, new CollectionEventRegister<T>());
    }

    protected DefaultDomainObjectCollection(Class<T> type, Collection<T> store, CollectionEventRegister<T> eventRegister) {
        this.type = type;
        this.store = store;
        this.eventRegister = eventRegister;
    }

    protected DefaultDomainObjectCollection(DefaultDomainObjectCollection<? super T> collection, CollectionFilter<T> filter) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter));
    }

    public Class<T> getType() {
        return type;
    }

    protected Collection<T> getStore() {
        return store;
    }

    protected CollectionEventRegister<T> getEventRegister() {
        return eventRegister;
    }

    protected CollectionFilter<T> createFilter(Spec<? super T> filter) {
        return createFilter(getType(), filter);
    }

    protected <S extends T> CollectionFilter<S> createFilter(Class<S> type) {
        return new CollectionFilter<S>(type);
    }

    protected <S extends T> CollectionFilter<S> createFilter(Class<S> type, Spec<? super S> spec) {
        return new CollectionFilter<S>(type, spec);
    }

    protected <S extends T> DefaultDomainObjectCollection<S> filtered(CollectionFilter<S> filter) {
        return new DefaultDomainObjectCollection<S>(this, filter);
    }

    protected <S extends T> Collection<S> filteredStore(CollectionFilter<S> filter) {
        return new FilteredCollection<T, S>(this, filter);
    }

    protected <S extends T> CollectionEventRegister<S> filteredEvents(CollectionFilter<S> filter) {
        return getEventRegister().filtered(filter);
    }

    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public DomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return filtered(createFilter(type));
    }

    public Set<T> getAll() {
        // DeprecationLogger.nagUser("DomainObjectCollection.getAll()");
        return findAll(Specs.<T>satisfyAll());
    }

    public Set<T> findAll(Spec<? super T> spec) {
        // DeprecationLogger.nagUser("DomainObjectCollection.findAll()", "all()");

        Set<T> filtered = new LinkedHashSet<T>(size());
        for (T t : getStore()) {
            if (spec.isSatisfiedBy(t)) {
                filtered.add(t);
            }
        }

        return filtered;
    }

    public Iterator<T> iterator() {
        return getStore().iterator();
    }

    public void allObjects(Action<? super T> action) {
        DeprecationLogger.nagUser("DomainObjectCollection.allObjects()", "all()");
        all(action);
    }

    public void allObjects(Closure action) {
        DeprecationLogger.nagUser("DomainObjectCollection.allObjects()", "all()");
        all(action);
    }

    public void all(Action<? super T> action) {
        action = whenObjectAdded(action);

        // copy in case any actions mutate the store
        // linked list because the underlying store may preserve order
        Collection<T> copied = new LinkedList<T>(getStore());

        for (T t : copied) {
            action.execute(t);
        }
    }

    public void all(Closure action) {
        all(toAction(action));
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        DomainObjectCollection<S> result = withType(type);
        result.all(configureAction);
        return result;
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        DomainObjectCollection<S> result = withType(type);
        result.all(configureClosure);
        return result;
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return eventRegister.registerAddAction(action);
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return eventRegister.registerRemoveAction(action);
    }

    public void whenObjectAdded(Closure action) {
        whenObjectAdded(toAction(action));
    }

    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(toAction(action));
    }

    private Action<? super T> toAction(final Closure action) {
        return new Action<T>() {
            public void execute(T t) {
                ConfigureUtil.configure(action, t);
            }
        };
    }

    public boolean add(T toAdd) {
        if (getStore().add(toAdd)) {
            eventRegister.getAddAction().execute(toAdd);
            return true;
        } else {
            return false;
        }
    }

    public boolean addAll(Collection<? extends T> c) {
        boolean changed = false;
        for (T o : c) {
            if (add(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public void clear() {
        Object[] c = toArray();
        getStore().clear();
        for (Object o : c) {
            eventRegister.getRemoveAction().execute((T)o);
        }
    }

    public boolean contains(Object o) {
        return getStore().contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return getStore().containsAll(c);
    }

    public boolean isEmpty() {
        return getStore().isEmpty();
    }

    public boolean remove(Object o) {
        if (getStore().remove(o)) {
            eventRegister.getRemoveAction().execute((T)o);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object o : c) {
            if (remove(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean retainAll(Collection<?> target) {
        Object[] existingItems = toArray();
        boolean changed = false;
        for (Object existingItem : existingItems) {
            if (!target.contains(existingItem)) {
                remove(existingItem);
                changed = true;
            }
        }
        return changed;
    }

    public int size() {
        return getStore().size();
    }

    public Object[] toArray() {
        return getStore().toArray();
    }

    public <T> T[] toArray(T[] a) {
        return getStore().toArray(a);
    }

}