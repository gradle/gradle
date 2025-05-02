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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.DefaultCollectionEventRegister;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.FilteredElementSource;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.DefaultListProperty;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.util.internal.ConfigureUtil;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class DefaultDomainObjectCollection<T> extends AbstractCollection<T> implements DomainObjectCollectionInternal<T> {

    private final Class<? extends T> type;
    private final CollectionEventRegister<T> eventRegister;

    /**
     * Stores all elements of this container, both lazy and eager.
     */
    private final ElementSource<T> store;

    /**
     * Actions to notify when this container is mutated.
     */
    private ImmutableActionSet<String> beforeContainerChange = ImmutableActionSet.empty();

    protected DefaultDomainObjectCollection(Class<? extends T> type, ElementSource<T> store, CollectionCallbackActionDecorator callbackActionDecorator) {
        this(type, store, new DefaultCollectionEventRegister<T>(type, callbackActionDecorator));
    }

    protected DefaultDomainObjectCollection(Class<? extends T> type, ElementSource<T> store, final CollectionEventRegister<T> eventRegister) {
        this.type = type;
        this.store = store;
        this.eventRegister = eventRegister;
        this.store.onPendingAdded(SerializableLambdas.action(toAdd -> {
            didAdd(toAdd);
            eventRegister.fireObjectAdded(toAdd);
        }));
        this.store.setSubscriptionVerifier(eventRegister);
    }

    protected DefaultDomainObjectCollection(DefaultDomainObjectCollection<? super T> collection, CollectionFilter<T> filter) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter));
    }

    protected void realized(ProviderInternal<? extends T> provider) {
        getStore().realizeExternal(provider);
    }

    public Class<? extends T> getType() {
        return type;
    }

    @Override
    public String getDisplayName() {
        return getTypeDisplayName() + " collection";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    protected String getTypeDisplayName() {
        return getType().getSimpleName();
    }

    protected ElementSource<T> getStore() {
        return store;
    }

    protected CollectionEventRegister<T> getEventRegister() {
        return eventRegister;
    }

    protected CollectionFilter<T> createFilter(Spec<? super T> filter) {
        return new CollectionFilter<>(type, eventRegister.getDecorator().decorateSpec(filter));
    }

    protected <S extends T> CollectionFilter<S> createFilter(Class<S> type) {
        return new CollectionFilter<>(type);
    }

    protected <S extends T> DefaultDomainObjectCollection<S> filtered(CollectionFilter<S> filter) {
        return new DefaultDomainObjectCollection<S>(this, filter);
    }

    protected <S extends T> ElementSource<S> filteredStore(final CollectionFilter<S> filter) {
        return filteredStore(filter, store);
    }

    protected <S extends T> ElementSource<S> filteredStore(CollectionFilter<S> filter, ElementSource<T> elementSource) {
        return new FilteredElementSource<T, S>(elementSource, filter);
    }

    protected <S extends T> CollectionEventRegister<S> filteredEvents(CollectionFilter<S> filter) {
        return eventRegister.filtered(filter);
    }

    @Override
    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public DomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public Iterator<T> iterator() {
        return new IteratorImpl(store.iterator());
    }

    Iterator<T> iteratorNoFlush() {
        if (store.constantTimeIsEmpty()) {
            return Collections.emptyIterator();
        }

        return new IteratorImpl(store.iteratorNoFlush());
    }

    @Override
    public void all(Action<? super T> action) {
        assertEagerContext("all(Action)");
        Action<? super T> decoratedAction = addEagerAction(action);

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
                copied = new ArrayList<>(estimatedSize());
            }
            copied.add(t);
        }
        if (copied != null) {
            for (T t : copied) {
                decoratedAction.execute(t);
            }
        }
    }

    @Override
    public void configureEach(Action<? super T> action) {
        assertEagerContext("configureEach(Action)");
        Action<? super T> wrappedAction = wrapLazyAction(decorate(action));
        Action<? super T> registerLazyAddActionDecorated = eventRegister.registerLazyAddAction(wrappedAction);

        // copy in case any actions mutate the store
        Collection<T> copied = null;
        Iterator<T> iterator = iteratorNoFlush();
        while (iterator.hasNext()) {
            // only create an intermediate collection if there's something to copy
            if (copied == null) {
                copied = new ArrayList<>(estimatedSize());
            }
            copied.add(iterator.next());
        }

        if (copied != null) {
            for (T next : copied) {
                registerLazyAddActionDecorated.execute(next);
            }
        }
    }

    protected <I extends T> Action<? super I> wrapLazyAction(Action<? super I> action) {
        return store.getLazyBehaviorGuard().wrapLazyAction(action);
    }

    @Override
    public void all(Closure action) {
        all(toAction(action));
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        assertEagerContext("withType(Class, Action)");
        DomainObjectCollection<S> result = withType(type);
        result.all(configureAction);
        return result;
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return withType(type, toAction(configureClosure));
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        assertEagerContext("whenObjectAdded(Action)");
        return addEagerAction(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        whenObjectAdded(toAction(action));
    }

    private Action<? super T> addEagerAction(Action<? super T> action) {
        store.realizePending(type);
        return eventRegister.registerEagerAddAction(type, decorate(action));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        eventRegister.registerRemoveAction(type, decorate(action));
        return action;
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(toAction(action));
    }

    private Action<? super T> decorate(Action<? super T> action) {
        return eventRegister.getDecorator().decorate(action);
    }

    private Action<? super T> toAction(Closure action) {
        return ConfigureUtil.configureUsing(action);
    }

    @Override
    public boolean add(T toAdd) {
        assertCanMutate("add(T)");
        return doAdd(toAdd, eventRegister.getAddActions());
    }

    protected <I extends T> boolean doAdd(I toAdd, Action<? super I> notification) {
        if (getStore().add(toAdd)) {
            didAdd(toAdd);
            notification.execute(toAdd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        assertCanMutate("addLater(Provider)");
        doAddLater(provider);
    }

    protected void doAddLater(Provider<? extends T> provider) {
        ProviderInternal<? extends T> providerInternal = Providers.internal(provider);
        store.addPending(providerInternal);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<T>> provider) {
        assertCanMutate("addAllLater(Provider)");
        final CollectionProviderInternal<T, ? extends Iterable<T>> providerInternal;
        if (provider instanceof CollectionProviderInternal) {
            providerInternal = Cast.uncheckedCast(provider);
        } else {
            // We don't know the type of element in the provider, so we assume it's the type of the collection
            DefaultListProperty<T> defaultListProperty = new DefaultListProperty<T>(PropertyHost.NO_OP, Cast.uncheckedCast(getType()));
            defaultListProperty.convention(provider);
            providerInternal = defaultListProperty;
        }
        store.addPendingCollection(providerInternal);
    }

    protected void didAdd(T toAdd) {
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        assertCanMutate("addAll(Collection)");
        boolean changed = false;
        for (T o : c) {
            if (doAdd(o, eventRegister.getAddActions())) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        assertCanMutate("clear()");
        if (store.constantTimeIsEmpty()) {
            return;
        }
        List<T> c = Lists.newArrayList(store.iteratorNoFlush());
        getStore().clear();
        for (T o : c) {
            eventRegister.fireObjectRemoved(o);
        }
    }

    @Override
    public boolean contains(Object o) {
        return getStore().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return getStore().containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        return getStore().isEmpty();
    }

    @Override
    public boolean remove(Object o) {
        assertCanMutate("remove(Object)");
        return doRemove(o);
    }

    private boolean doRemove(Object o) {
        if (o instanceof ProviderInternal) {
            ProviderInternal<? extends T> providerInternal = Cast.uncheckedCast(o);
            if (getStore().removePending(providerInternal)) {
                // NOTE: When removing provider, we don't need to fireObjectRemoved as they were never added in the first place.
                didRemove(providerInternal);
                return true;
            } else if (getType().isAssignableFrom(providerInternal.getType()) && providerInternal.isPresent()) {
                // The provider is of compatible type and the element was either already realized or we are removing a provider to the element
                o = providerInternal.get();
            }
            // Else, the provider is of incompatible type, maybe we have a domain object collection of Provider, fallthrough
        }

        if (getStore().remove(o)) {
            @SuppressWarnings("unchecked") T cast = (T) o;
            didRemove(cast);
            eventRegister.fireObjectRemoved(cast);
            return true;
        } else {
            return false;
        }
    }

    protected void didRemove(T t) {
    }

    protected void didRemove(ProviderInternal<? extends T> t) {
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        assertCanMutate("removeAll(Collection)");
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

    @Override
    public boolean retainAll(Collection<?> target) {
        assertCanMutate("retainAll(Collection)");
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

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public int estimatedSize() {
        return store.estimatedSize();
    }

    @Override
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

    /**
     * Asserts that the method with the given name, which performs mutation on this
     * container, may proceed.
     */
    protected final void assertCanMutate(String methodName) {
        // beforeContainerChange controls the mutability of this container.
        // It should throw an exception if mutation is forbidden.
        beforeContainerChange.execute(methodName);

        // We also restrict mutations to only occur in eager contexts.
        // Users cannot mutate the container within a lazy callback.
        assertEagerContext(methodName);
    }

    /**
     * Assert that the current thread is not running a lazy action.
     * This method should be called by methods that must not be called in lazy actions.
     */
    protected final void assertEagerContext(String methodName) {
        store.getLazyBehaviorGuard().assertEagerContext(methodName, this);
    }

    /**
     * Register an action to be executed before this collection is mutated.
     * Registered actions may throw exceptions in order to forbid this container from mutating.
     *
     * TODO: Merge this functionality with MutationValidator.
     */
    @Override
    public void beforeCollectionChanges(Action<String> action) {
        beforeContainerChange = beforeContainerChange.add(action);
    }

    protected class IteratorImpl implements Iterator<T> {
        private final Iterator<T> iterator;
        private T currentElement;

        public IteratorImpl(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            currentElement = iterator.next();
            return currentElement;
        }

        @Override
        public void remove() {
            assertCanMutate("iterator().remove()");
            iterator.remove();
            didRemove(currentElement);
            getEventRegister().fireObjectRemoved(currentElement);
            currentElement = null;
        }
    }


}
