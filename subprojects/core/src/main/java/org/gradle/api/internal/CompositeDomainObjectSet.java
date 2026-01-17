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

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.EventSubscriptionVerifier;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A domain object collection that presents a combined view of one or more collections.  {@link DomainObjectCollection} objects can be
 * added or removed from this set, but individual elements cannot.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DelegatingDomainObjectSet<T> {

    private final Spec<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private final Spec<T> notInSpec = new ItemNotInCompositeSpec();

    private final CollectionCallbackActionDecorator callbackActionDecorator;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        return create(type, CollectionCallbackActionDecorator.NOOP, collections);
    }

    @SafeVarargs
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, CollectionCallbackActionDecorator callbackActionDecorator, DomainObjectCollection<? extends T>... collections) {
        DefaultDomainObjectSet<T> delegate = new DefaultDomainObjectSet<T>(type, new DomainObjectCompositeCollection<T>(), callbackActionDecorator);
        CompositeDomainObjectSet<T> out = new CompositeDomainObjectSet<T>(delegate, callbackActionDecorator);
        for (DomainObjectCollection<? extends T> c : collections) {
            out.addCollection(c);
        }
        return out;
    }

    private CompositeDomainObjectSet(DefaultDomainObjectSet<T> delegate, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(delegate);
        this.callbackActionDecorator = callbackActionDecorator;
    }

    public class ItemIsUniqueInCompositeSpec implements Spec<T> {
        @Override
        public boolean isSatisfiedBy(T element) {
            int matches = 0;
            for (DomainObjectCompositeCollection.StoredCollection<T> stored : getStore().store) {
                if (stored.getWithoutSideEffects().contains(element)) {
                    if (++matches > 1) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public class ItemNotInCompositeSpec implements Spec<T> {
        @Override
        public boolean isSatisfiedBy(T element) {
            return !getStore().contains(element);
        }
    }

    @Override
    protected DefaultDomainObjectSet<T> getDelegate() {
        return (DefaultDomainObjectSet<T>) super.getDelegate();
    }

    @SuppressWarnings("unchecked")
    protected DomainObjectCompositeCollection<T> getStore() {
        return (DomainObjectCompositeCollection) getDelegate().getStore();
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().containsCollection(collection)) {
            getStore().addComposited((DomainObjectCollectionInternal<? extends T>) collection);
            collection.all((InternalAction<T>) t -> getDelegate().getEventRegister().fireObjectAdded(t));
            collection.whenObjectRemoved((Action<T>) t -> getDelegate().getEventRegister().fireObjectRemoved(t));
        }
    }

    public void addCollectionProvider(Provider<DomainObjectCollection<? extends T>> collectionProvider) {
        if (!getStore().containsCollectionProvider(collectionProvider)) {
            getStore().addComposited(collectionProvider, collection -> {
                collection.all((InternalAction<T>) t -> getDelegate().getEventRegister().fireObjectAdded(t));
                collection.whenObjectRemoved((Action<T>) t -> getDelegate().getEventRegister().fireObjectRemoved(t));
            });
        }
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        for (T item : collection) {
            getDelegate().getEventRegister().fireObjectRemoved(item);
        }
    }

    public void removeCollectionProvider(Provider<DomainObjectCollection<? extends T>> collectionProvider) {
        getStore().removeComposited(collectionProvider, collection -> {
            for (T item : collection) {
                getDelegate().getEventRegister().fireObjectRemoved(item);
            }
        });
    }

    @Override
    public Iterator<T> iterator() {
        return getStore().iterator();
    }

    /**
     * {@inheritDoc}
     *  <p>
     * This method is expensive. Avoid calling it if possible. If all you need is a rough
     * estimate, call {@link #estimatedSize()} instead.
     */
    @Override
    public int size() {
        return getStore().size();
    }

    @Override
    public int estimatedSize() {
        return getStore().estimatedSize();
    }

    @Override
    public void all(Action<? super T> action) {
        //calling overloaded method with extra behavior:
        for (T t : this) {
            callbackActionDecorator.decorate(action).execute(t);
        }
        whenObjectAdded(action);
    }

    private final static class DomainObjectCompositeCollection<T> implements ElementSource<T> {

        private final List<StoredCollection<T>> store = new LinkedList<>();

        interface StoredCollection<T> {
            /**
             * Returns the collection, executing any side-effects associated with its realization.
             */
            DomainObjectCollection<? extends T> get();
            /**
             * Returns the collection without executing any side-effects associated with its realization.  This should be used only for actions
             * performed during collection realization, such as firing events for newly realized elements.
             */
            DomainObjectCollection<? extends T> getWithoutSideEffects();
        }

        static class RealizedCollection<T> implements StoredCollection<T> {
            private final DomainObjectCollection<? extends T> collection;

            RealizedCollection(DomainObjectCollection<? extends T> collection) {
                this.collection = collection;
            }

            @Override
            public DomainObjectCollection<? extends T> get() {
                return collection;
            }

            @Override
            public DomainObjectCollection<? extends T> getWithoutSideEffects() {
                return collection;
            }
        }

        static class ProvidedCollection<T> implements StoredCollection<T> {
            private final Provider<DomainObjectCollection<? extends T>> provider;
            private final Action<DomainObjectCollection<? extends T>> onRealization;
            private boolean realized;

            @Nullable
            private DomainObjectCollection<? extends T> collection = null;

            ProvidedCollection(Provider<DomainObjectCollection<? extends T>> provider, Action<DomainObjectCollection<? extends T>> onRealization) {
                this.provider = provider;
                this.onRealization = onRealization;
            }

            @Override
            public DomainObjectCollection<? extends T> get() {
                if (collection == null) {
                    collection = provider.get();
                }
                if (!realized) {
                    onRealization.execute(collection);
                    realized = true;
                }
                return collection;
            }

            @Override
            public DomainObjectCollection<? extends T> getWithoutSideEffects() {
                if (collection == null) {
                    collection = provider.get();
                }
                return collection;
            }

            public Provider<DomainObjectCollection<? extends T>> getProvider() {
                return provider;
            }
        }

        public boolean containsCollection(DomainObjectCollection<? extends T> collection) {
            return store.stream()
                .filter(it -> it instanceof RealizedCollection)
                .map(StoredCollection::get)
                .anyMatch(stored -> stored == collection);
        }

        public boolean containsCollectionProvider(Provider<?> collectionProvider) {
            return store.stream()
                .filter(it -> it instanceof ProvidedCollection)
                .map(it -> ((ProvidedCollection<?>) it).getProvider())
                .anyMatch(stored -> stored == collectionProvider);
        }

        @SuppressWarnings("MixedMutabilityReturnType")
        Set<T> collect() {
            if (store.isEmpty()) {
                return Collections.emptySet();
            }
            Set<T> tmp = Sets.newLinkedHashSetWithExpectedSize(estimatedSize());
            for (StoredCollection<T> collection : store) {
                tmp.addAll(collection.get());
            }
            return tmp;
        }

        @Override
        public int size() {
            return collect().size();
        }

        @Override
        public boolean isEmpty() {
            return store.stream().allMatch(it -> it.get().isEmpty());
        }

        @Override
        public boolean contains(Object o) {
            return store.stream().anyMatch(it -> it.get().contains(o));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            if (store.isEmpty()) {
                return Collections.emptyIterator();
            }
            if (store.size() == 1) {
                return (Iterator<T>) store.get(0).get().iterator();
            }
            return collect().iterator();
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
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public void addComposited(DomainObjectCollectionInternal<? extends T> collection) {
            this.store.add(new RealizedCollection<>(collection));
        }

        public void addComposited(Provider<DomainObjectCollection<? extends T>> collectionProvider, Action<DomainObjectCollection<? extends T>> onRealization) {
            this.store.add(new ProvidedCollection<>(collectionProvider, onRealization));
        }

        public void removeComposited(DomainObjectCollection<? extends T> collection) {
            Iterator<StoredCollection<T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                StoredCollection<T> next = iterator.next();
                if (next instanceof RealizedCollection && next.get() == collection) {
                    iterator.remove();
                    break;
                }
            }
        }

        public void removeComposited(Provider<DomainObjectCollection<? extends T>> collectionProvider, Action<DomainObjectCollection<? extends T>> onRealized) {
            Iterator<StoredCollection<T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                StoredCollection<T> next = iterator.next();
                if (next instanceof ProvidedCollection) {
                    ProvidedCollection<T> provided = (ProvidedCollection<T>) next;
                    if (provided.getProvider() == collectionProvider) {
                        iterator.remove();
                        if (provided.realized) {
                            onRealized.execute(provided.getProvider().get());
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public boolean constantTimeIsEmpty() {
            return store.isEmpty();
        }

        @Override
        public int estimatedSize() {
            return store.stream().mapToInt(stored ->
                ((DomainObjectCollectionInternal<? extends T>)stored.getWithoutSideEffects()).estimatedSize()
            ).sum();
        }

        @Override
        public Iterator<T> iteratorNoFlush() {
            return iterator();
        }

        @Override
        public void realizePending() {

        }

        @Override
        public void realizePending(Class<?> type) {

        }

        @Override
        public boolean addPending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPendingAdded(Action<T> action) {

        }

        @Override
        public void setSubscriptionVerifier(EventSubscriptionVerifier<T> immediateRealizationSpec) {

        }

        @Override
        public void realizeExternal(ProviderInternal<? extends T> provider) {

        }

        @Override
        public MutationGuard getLazyBehaviorGuard() {
            return MutationGuards.identity();
        }
    }
}
