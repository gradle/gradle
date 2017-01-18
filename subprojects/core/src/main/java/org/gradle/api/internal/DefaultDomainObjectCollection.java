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
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.FilteredCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.FastActionSet;
import org.gradle.util.ConfigureUtil;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class DefaultDomainObjectCollection<T> extends AbstractCollection<T> implements DomainObjectCollection<T>, WithEstimatedSize {

    private final Class<? extends T> type;
    private final CollectionEventRegister<T> eventRegister;
    private final Collection<T> store;
    private final boolean hasConstantTimeSizeMethod;
    private final FastActionSet<Void> mutateAction = new FastActionSet<Void>();

    public DefaultDomainObjectCollection(Class<? extends T> type, Collection<T> store) {
        this(type, store, new CollectionEventRegister<T>());
    }

    protected DefaultDomainObjectCollection(Class<? extends T> type, Collection<T> store, CollectionEventRegister<T> eventRegister) {
        this.type = type;
        this.store = store;
        this.eventRegister = eventRegister;
        this.hasConstantTimeSizeMethod = Estimates.isKnownToHaveConstantTimeSizeMethod(store);
    }

    protected DefaultDomainObjectCollection(DefaultDomainObjectCollection<? super T> collection, CollectionFilter<T> filter) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter));
    }

    public Class<? extends T> getType() {
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

    protected <S extends T> CollectionFilter<S> createFilter(Class<? extends S> type, Spec<? super S> spec) {
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

    public Iterator<T> iterator() {
        if (constantTimeIsEmpty()) {
            return Iterators.emptyIterator();
        }
        return new IteratorImpl(getStore().iterator());
    }

    public void all(Action<? super T> action) {

        action = whenObjectAdded(action);

        if (constantTimeIsEmpty()) {
            return;
        }

        // copy in case any actions mutate the store
        // linked list because the underlying store may preserve order
        // We make best effort not to create an intermediate collection if this container
        // is empty.
        Collection<T> copied = null;
        for (T t : this) {
            if (copied == null) {
                copied = Lists.newArrayListWithExpectedSize(estimatedSize());
            }
            copied.add(t);
        }
        if (copied != null) {
            for (T t : copied) {
                action.execute(t);
            }
        }
    }

    /**
     * Returns true if, and only if, the store is empty AND we know that we
     * can query its size in constant time. Otherwise it returns false, which means
     * that the collection may contain elements or may be empty (we don't know without
     * spending too much time).
     *
     * @return true if and only if the store is empty and can tell in constant time
     */
    private boolean constantTimeIsEmpty() {
        return hasConstantTimeSizeMethod && store.isEmpty();
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

    /**
     * Adds an action which is executed before this collection is mutated. Any exception thrown by the action will veto the mutation.
     */
    public void beforeChange(Action<Void> action) {
        mutateAction.add(action);
    }

    private Action<? super T> toAction(Closure action) {
        return ConfigureUtil.configureUsing(action);
    }

    public boolean add(T toAdd) {
        assertMutable();
        return doAdd(toAdd);
    }

    private boolean doAdd(T toAdd) {
        if (getStore().add(toAdd)) {
            didAdd(toAdd);
            eventRegister.getAddAction().execute(toAdd);
            return true;
        } else {
            return false;
        }
    }

    protected void didAdd(T toAdd) {
    }

    public boolean addAll(Collection<? extends T> c) {
        assertMutable();
        boolean changed = false;
        for (T o : c) {
            if (doAdd(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public void clear() {
        assertMutable();
        if (constantTimeIsEmpty()) {
            return;
        }
        Object[] c = toArray();
        getStore().clear();
        for (Object o : c) {
            eventRegister.getRemoveAction().execute((T) o);
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
        assertMutable();
        return doRemove(o);
    }

    private boolean doRemove(Object o) {
        if (getStore().remove(o)) {
            @SuppressWarnings("unchecked") T cast = (T) o;
            didRemove(cast);
            eventRegister.getRemoveAction().execute(cast);
            return true;
        } else {
            return false;
        }
    }

    protected void didRemove(T t) {
    }

    public boolean removeAll(Collection<?> c) {
        assertMutable();
        if (constantTimeIsEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Object o : c) {
            if (doRemove(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean retainAll(Collection<?> target) {
        assertMutable();
        Object[] existingItems = toArray();
        boolean changed = false;
        for (Object existingItem : existingItems) {
            if (!target.contains(existingItem)) {
                doRemove(existingItem);
                changed = true;
            }
        }
        return changed;
    }

    public int size() {
        return getStore().size();
    }

    @Override
    public int estimatedSize() {
        return Estimates.estimateSizeOf(getStore());
    }


    public Collection<T> findAll(Closure cl) {
        return findAll(cl, new ArrayList<T>());
    }

    protected <S extends Collection<? super T>> S findAll(Closure cl, S matches) {
        if (constantTimeIsEmpty()) {
            return matches;
        }
        for (T t : filteredStore(createFilter(Specs.<Object>convertClosureToSpec(cl)))) {
            matches.add(t);
        }
        return matches;
    }

    protected void assertMutable() {
        if (mutateAction != null) {
            mutateAction.execute(null);
        }
    }

    protected class IteratorImpl implements Iterator<T>, WithEstimatedSize {
        private final Iterator<T> iterator;
        private T currentElement;

        public IteratorImpl(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public T next() {
            currentElement = iterator.next();
            return currentElement;
        }

        public void remove() {
            assertMutable();
            iterator.remove();
            didRemove(currentElement);
            getEventRegister().getRemoveAction().execute(currentElement);
            currentElement = null;
        }

        @Override
        public int estimatedSize() {
            return DefaultDomainObjectCollection.this.estimatedSize();
        }
    }
}
