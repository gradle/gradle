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
import org.gradle.api.internal.collections.BroadcastingCollectionEventRegister;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.FilteredCollection;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.util.ConfigureUtil;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class DefaultDomainObjectCollection<T> extends AbstractCollection<T> implements DomainObjectCollection<T>, WithEstimatedSize {

    private final Class<? extends T> type;
    private final CollectionEventRegister<T> eventRegister;
    private final ElementSource<T> store;
    private ImmutableActionSet<Void> mutateAction = ImmutableActionSet.empty();
    private List<ProviderInternal<? extends T>> pending;

    protected DefaultDomainObjectCollection(Class<? extends T> type, ElementSource<T> store) {
        this(type, store, new BroadcastingCollectionEventRegister<T>(type));
    }

    protected DefaultDomainObjectCollection(Class<? extends T> type, ElementSource<T> store, CollectionEventRegister<T> eventRegister) {
        this.type = type;
        this.store = store;
        this.eventRegister = eventRegister;
    }

    protected DefaultDomainObjectCollection(DefaultDomainObjectCollection<? super T> collection, CollectionFilter<T> filter) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter));
    }

    public Class<? extends T> getType() {
        return type;
    }

    protected ElementSource<T> getStore() {
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

    protected <S extends T> ElementSource<S> filteredStore(final CollectionFilter<S> filter) {
        return filteredStore(filter, new FlushingElementSource(filter));
    }

    protected <S extends T> ElementSource<S> filteredStore(CollectionFilter<S> filter, ElementSource<T> elementSource) {
        return new FilteredCollection<T, S>(elementSource, filter);
    }

    protected <S extends T> CollectionEventRegister<S> filteredEvents(CollectionFilter<S> filter) {
        return new FlushingEventRegister<S>(filter, getEventRegister());
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
        flushPending();
        return iteratorNoFlush();
    }

    Iterator<T> iteratorNoFlush() {
        if (store.constantTimeIsEmpty()) {
            return Iterators.emptyIterator();
        }
        return new IteratorImpl(store.iterator());
    }

    private void flushPending() {
        if (pending != null) {
            for (ProviderInternal<? extends T> provider : pending) {
                doAdd(provider.get());
            }
            pending.clear();
        }
    }

    private void flushPending(Class<?> type) {
        if (pending != null) {
            ListIterator<ProviderInternal<? extends T>> iterator = pending.listIterator();
            while (iterator.hasNext()) {
                ProviderInternal<? extends T> provider = iterator.next();
                if (provider.getType() == null || type.isAssignableFrom(provider.getType())) {
                    doAdd(provider.get());
                    iterator.remove();
                }
            }
        }
    }

    public void all(Action<? super T> action) {

        action = whenObjectAdded(action);

        if (store.constantTimeIsEmpty()) {
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

    @Override
    public void configureEachLater(Action<? super T> action) {
        eventRegister.registerLazyAddAction(action);
        Iterator<T> iterator = iteratorNoFlush();
        while (iterator.hasNext()) {
            T next = iterator.next();
            action.execute(next);
        }
    }

    @Override
    public <S extends T> void configureEachLater(Class<S> type, Action<? super S> action) {
        withType(type).configureEachLater(action);
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
        eventRegister.registerEagerAddAction(type, action);
        return action;
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        eventRegister.registerRemoveAction(type, action);
        return action;
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
        mutateAction = mutateAction.add(action);
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

    @Override
    public void addLater(Provider<? extends T> provider) {
        ProviderInternal<? extends T> providerInternal = Cast.uncheckedCast(provider);
        if (eventRegister.isSubscribed(providerInternal.getType())) {
            doAdd(provider.get());
            return;
        }
        if (pending == null) {
            pending = new ArrayList<ProviderInternal<? extends T>>();
        }
        pending.add(providerInternal);
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
        if (store.constantTimeIsEmpty()) {
            return;
        }
        Object[] c = toArray();
        getStore().clear();
        for (Object o : c) {
            eventRegister.getRemoveAction().execute((T) o);
        }
    }

    public boolean contains(Object o) {
        flushPending();
        return getStore().contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return getStore().containsAll(c);
    }

    public boolean isEmpty() {
        return getStore().isEmpty() && (pending == null || pending.isEmpty());
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
        if (store.constantTimeIsEmpty()) {
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
        return store.size() + (pending == null ? 0 : pending.size());
    }

    @Override
    public int estimatedSize() {
        return store.estimatedSize() + (pending == null ? 0 : pending.size());
    }

    public Collection<T> findAll(Closure cl) {
        return findAll(cl, new ArrayList<T>());
    }

    protected <S extends Collection<? super T>> S findAll(Closure cl, S matches) {
        if (store.constantTimeIsEmpty()) {
            return matches;
        }
        for (T t : filteredStore(createFilter(Specs.<Object>convertClosureToSpec(cl)))) {
            matches.add(t);
        }
        return matches;
    }

    protected void assertMutable() {
        mutateAction.execute(null);
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

    private class FlushingEventRegister<S extends T> implements CollectionEventRegister<S> {
        private final CollectionFilter<S> filter;
        private final CollectionEventRegister<S> delegate;

        FlushingEventRegister(CollectionFilter<S> filter, CollectionEventRegister<T> delegate) {
            this.filter = filter;
            this.delegate = Cast.uncheckedCast(delegate);
        }

        @Override
        public Action<S> getAddAction() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Action<S> getRemoveAction() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSubscribed(Class<?> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerEagerAddAction(Class<? extends S> type, Action<? super S> addAction) {
            // Any elements previously added should not be visible to the action
            flushPending(filter.getType());
            delegate.registerEagerAddAction(filter.getType(), Actions.filter(addAction, filter));
        }

        @Override
        public void registerLazyAddAction(Action<? super S> addAction) {
            delegate.registerLazyAddAction(Actions.filter(addAction, filter));
        }

        @Override
        public void registerRemoveAction(Class<? extends S> type, Action<? super S> removeAction) {
            delegate.registerRemoveAction(filter.getType(), Actions.filter(removeAction, filter));
        }
    }

    private class FlushingElementSource implements ElementSource<T> {
        private final CollectionFilter<?> filter;

        FlushingElementSource(CollectionFilter<?> filter) {
            this.filter = filter;
        }

        @Override
        public int estimatedSize() {
            return DefaultDomainObjectCollection.this.estimatedSize();
        }

        @Override
        public int size() {
            return DefaultDomainObjectCollection.this.size();
        }

        @Override
        public boolean contains(Object element) {
            return DefaultDomainObjectCollection.this.contains(element);
        }

        @Override
        public boolean containsAll(Collection<?> elements) {
            return DefaultDomainObjectCollection.this.containsAll(elements);
        }

        @Override
        public boolean isEmpty() {
            return DefaultDomainObjectCollection.this.isEmpty();
        }

        @Override
        public boolean constantTimeIsEmpty() {
            return store.constantTimeIsEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            flushPending(filter.getType());
            return iteratorNoFlush();
        }

        @Override
        public boolean add(T element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}
