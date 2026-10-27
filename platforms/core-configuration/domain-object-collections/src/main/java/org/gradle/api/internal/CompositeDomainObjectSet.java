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

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.EventSubscriptionVerifier;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.util.internal.ConfigureUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        return create(type, CollectionCallbackActionDecorator.NOOP, collections);
    }

    @SafeVarargs
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, CollectionCallbackActionDecorator callbackActionDecorator, DomainObjectCollection<? extends T>... collections) {
        DefaultDomainObjectSet<T> delegate = new DefaultDomainObjectSet<T>(type, new DomainObjectCompositeCollection<T>(), callbackActionDecorator);
        CompositeDomainObjectSet<T> out = new CompositeDomainObjectSet<T>(delegate);
        for (DomainObjectCollection<? extends T> c : collections) {
            out.addCollection(c);
        }
        return out;
    }

    private CompositeDomainObjectSet(DefaultDomainObjectSet<T> delegate) {
        super(delegate);
    }

    // Only counts the elements the composited collections have announced already: realizing others here would announce them
    // while the check is running, so each announcement would see the other one, and the element would not be announced at all
    public class ItemIsUniqueInCompositeSpec implements Spec<T> {
        @Override
        public boolean isSatisfiedBy(T element) {
            int matches = 0;
            for (DomainObjectCollectionInternal<? extends T> collection : getStore().wiredCollections()) {
                if (collection.containsRealized(element)) {
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
            // Unlike for additions, provided collections that are not wired yet count as well, but nothing is realized
            return !getStore().containsRealizedIncludingUnwired(element);
        }
    }

    @Override
    protected DefaultDomainObjectSet<T> getDelegate() {
        return (DefaultDomainObjectSet<T>) super.getDelegate();
    }

    @SuppressWarnings({"unchecked", "ExposedPrivateType"})
    protected DomainObjectCompositeCollection<T> getStore() {
        return (DomainObjectCompositeCollection) getDelegate().getStore();
    }

    @Override
    public void whenObjectAdded(Closure action) {
        whenObjectAdded(ConfigureUtil.configureUsing(action));
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(ConfigureUtil.configureUsing(action));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().containsCollection(collection)) {
            DomainObjectCompositeCollection.StoredCollection<T> stored = getStore().addComposited((DomainObjectCollectionInternal<? extends T>) collection);
            collection.whenObjectRemoved((Action<T>) t -> getDelegate().getEventRegister().fireObjectRemoved(t));
            forwardAdditions(collection, stored);
        }
    }

    public void addCollectionProvider(Provider<DomainObjectCollection<? extends T>> collectionProvider) {
        if (!getStore().containsCollectionProvider(collectionProvider)) {
            getStore().addComposited(collectionProvider, stored -> {
                DomainObjectCollection<? extends T> collection = stored.getWithoutSideEffects();
                // Registered first, wiring the collection may fail later on
                collection.whenObjectRemoved((Action<T>) t -> getDelegate().getEventRegister().fireObjectRemoved(t));
                forwardAdditions(collection, stored);
            });
        }
    }

    // Eager listeners of this composite count as eager listeners of the collection
    private void forwardAdditions(DomainObjectCollection<? extends T> collection, DomainObjectCompositeCollection.StoredCollection<T> stored) {
        DomainObjectCollectionInternal<? extends T> source = (DomainObjectCollectionInternal<? extends T>) collection;
        try {
            source.forwardElements(
                (InternalAction<T>) t -> {
                    // A collection that was removed, possibly by a listener while it was wired, is not part of this one anymore
                    if (getStore().isComposited(stored)) {
                        stored.announcements++;
                        getDelegate().getEventRegister().fireObjectAdded(t);
                    }
                },
                subscriptionsFor(stored)
            );
        } finally {
            // Removed by a listener while it was wired, possibly before the forwarding was registered
            if (!getStore().isComposited(stored)) {
                stopForwarding(stored, collection);
            }
        }
    }

    // Each composited collection gets its own, so stopping the forwarding of one doesn't affect another one, e.g. the same collection added again
    private EventSubscriptionVerifier<T> subscriptionsFor(DomainObjectCompositeCollection.StoredCollection<T> stored) {
        if (stored.subscriptions == null) {
            CollectionEventRegister<T> eventRegister = getDelegate().getEventRegister();
            // Not the register itself: the identity is what tells the composited collections apart
            stored.subscriptions = type -> eventRegister.isSubscribed(type);
        }
        return stored.subscriptions;
    }

    private void stopForwarding(DomainObjectCompositeCollection.StoredCollection<T> stored, DomainObjectCollection<? extends T> collection) {
        if (stored.subscriptions != null) {
            ((DomainObjectCollectionInternal<? extends T>) collection).stopForwardingElements(stored.subscriptions);
        }
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        DomainObjectCompositeCollection.StoredCollection<T> removed = getStore().removeComposited(collection);
        if (removed != null) {
            stopForwarding(removed, collection);
        }
        for (T item : collection) {
            getDelegate().getEventRegister().fireObjectRemoved(item);
        }
    }

    public void removeCollectionProvider(Provider<DomainObjectCollection<? extends T>> collectionProvider) {
        getStore().removeComposited(collectionProvider, stored -> {
            DomainObjectCollection<? extends T> collection = stored.getWithoutSideEffects();
            stopForwarding(stored, collection);
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
    public void all(Closure action) {
        all(ConfigureUtil.configureUsing(action));
    }

    @Override
    public void all(Action<? super T> action) {
        // The current elements are listed once, an added one is passed on only if no other composited collection has it already, like for whenObjectAdded()
        getDelegate().all(action, uniqueSpec);
    }

    @Override
    public void forwardElements(Action<? super T> action, EventSubscriptionVerifier<? super T> targetSubscriptions) {
        // Like all(): the realized elements are listed once, and an added one only if no other composited collection has it already
        getDelegate().forwardElements(action, uniqueSpec, targetSubscriptions);
    }

    private final static class DomainObjectCompositeCollection<T> implements ElementSource<T> {

        private final List<StoredCollection<T>> store = new LinkedList<>();

        abstract static class StoredCollection<T> {
            // Counts the elements the collection announced, so a read can tell whether listeners added any after it was read
            int announcements;
            // What the forwarding to the composite uses, see subscriptionsFor()
            @Nullable
            EventSubscriptionVerifier<T> subscriptions;

            /**
             * Returns the collection, executing any side-effects associated with its realization.
             */
            abstract DomainObjectCollection<? extends T> get();
            /**
             * Returns the collection without executing any side-effects associated with its realization.  This should be used only for actions
             * performed during collection realization, such as firing events for newly realized elements.
             */
            abstract DomainObjectCollection<? extends T> getWithoutSideEffects();
        }

        static class RealizedCollection<T> extends StoredCollection<T> {
            private final DomainObjectCollection<? extends T> collection;

            RealizedCollection(DomainObjectCollection<? extends T> collection) {
                this.collection = collection;
            }

            @Override
            DomainObjectCollection<? extends T> get() {
                return collection;
            }

            @Override
            DomainObjectCollection<? extends T> getWithoutSideEffects() {
                return collection;
            }
        }

        static class ProvidedCollection<T> extends StoredCollection<T> {
            private final Provider<DomainObjectCollection<? extends T>> provider;
            private final Action<StoredCollection<T>> onRealization;
            private boolean realized;

            @Nullable
            private DomainObjectCollection<? extends T> collection = null;

            ProvidedCollection(Provider<DomainObjectCollection<? extends T>> provider, Action<StoredCollection<T>> onRealization) {
                this.provider = provider;
                this.onRealization = onRealization;
            }

            @Override
            DomainObjectCollection<? extends T> get() {
                if (collection == null) {
                    collection = provider.get();
                }
                if (!realized) {
                    // Marked first: while it is wired, its elements already count as part of the composite
                    realized = true;
                    onRealization.execute(this);
                }
                return collection;
            }

            @Override
            DomainObjectCollection<? extends T> getWithoutSideEffects() {
                if (collection == null) {
                    collection = provider.get();
                }
                return collection;
            }

            public Provider<DomainObjectCollection<? extends T>> getProvider() {
                return provider;
            }
        }

        // Reading the collections may realize their elements, and listeners may then change which collections are composited
        List<StoredCollection<T>> snapshot() {
            return new ArrayList<>(store);
        }

        // Provided collections that are not wired yet are left until they are read, which announces their elements
        List<DomainObjectCollectionInternal<? extends T>> wiredCollections() {
            List<DomainObjectCollectionInternal<? extends T>> wired = new ArrayList<>(store.size());
            for (StoredCollection<T> stored : store) {
                if (isWired(stored)) {
                    wired.add((DomainObjectCollectionInternal<? extends T>) stored.get());
                }
            }
            return wired;
        }

        private static boolean isWired(StoredCollection<?> stored) {
            return !(stored instanceof ProvidedCollection) || ((ProvidedCollection<?>) stored).realized;
        }

        boolean isComposited(StoredCollection<T> stored) {
            return store.contains(stored);
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
            // Reading a collection may realize its pending elements, and listeners may then add elements to collections
            // that were read already, so the collections are read again until that does not happen
            while (true) {
                List<StoredCollection<T>> collections = snapshot();
                int[] announcementsWhenRead = new int[collections.size()];
                ImmutableSet.Builder<T> tmp = ImmutableSet.builderWithExpectedSize(estimatedSize());
                for (int index = 0; index < collections.size(); index++) {
                    tmp.addAll(collections.get(index).get());
                    announcementsWhenRead[index] = collections.get(index).announcements;
                }
                if (!changedSince(collections, announcementsWhenRead)) {
                    return tmp.build();
                }
            }
        }

        // After a read, everything is wired and realized, so a collection that is not wired or has pending elements changed afterwards
        private boolean changedSince(List<StoredCollection<T>> collections, int[] announcementsWhenRead) {
            return announcedSince(collections, announcementsWhenRead) || hasPendingElements();
        }

        private boolean announcedSince(List<StoredCollection<T>> collections, int[] announcementsWhenRead) {
            if (!collections.equals(store)) {
                return true;
            }
            for (int index = 0; index < collections.size(); index++) {
                if (collections.get(index).announcements != announcementsWhenRead[index]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int size() {
            return collect().size();
        }

        @Override
        public boolean isEmpty() {
            return store.stream().allMatch(it -> it.getWithoutSideEffects().isEmpty());
        }

        @Override
        public boolean contains(Object o) {
            // Like collect(), the collections are checked again if listeners may have added the element to one checked already
            while (true) {
                List<StoredCollection<T>> collections = snapshot();
                int[] announcementsWhenChecked = new int[collections.size()];
                for (int index = 0; index < collections.size(); index++) {
                    if (collections.get(index).getWithoutSideEffects().contains(o)) {
                        return true;
                    }
                    announcementsWhenChecked[index] = collections.get(index).announcements;
                }
                // Checking doesn't wire provided collections, so these are not taken as changes here
                if (!announcedSince(collections, announcementsWhenChecked)) {
                    return false;
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            if (store.isEmpty()) {
                return Collections.emptyIterator();
            }
            if (store.size() == 1) {
                // Reading the only collection may change it or the composited collections too, then all of them are read again
                List<StoredCollection<T>> collections = snapshot();
                Iterator<T> iterator = (Iterator<T>) collections.get(0).get().iterator();
                if (!changedSince(collections, new int[] {collections.get(0).announcements})) {
                    return iterator;
                }
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

        public StoredCollection<T> addComposited(DomainObjectCollectionInternal<? extends T> collection) {
            StoredCollection<T> stored = new RealizedCollection<>(collection);
            this.store.add(stored);
            return stored;
        }

        public void addComposited(Provider<DomainObjectCollection<? extends T>> collectionProvider, Action<StoredCollection<T>> onRealization) {
            this.store.add(new ProvidedCollection<>(collectionProvider, onRealization));
        }

        @Nullable
        public StoredCollection<T> removeComposited(DomainObjectCollection<? extends T> collection) {
            Iterator<StoredCollection<T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                StoredCollection<T> next = iterator.next();
                if (next instanceof RealizedCollection && next.get() == collection) {
                    iterator.remove();
                    return next;
                }
            }
            return null;
        }

        public void removeComposited(Provider<DomainObjectCollection<? extends T>> collectionProvider, Action<StoredCollection<T>> onRealized) {
            Iterator<StoredCollection<T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                StoredCollection<T> next = iterator.next();
                if (next instanceof ProvidedCollection) {
                    ProvidedCollection<T> provided = (ProvidedCollection<T>) next;
                    if (provided.getProvider() == collectionProvider) {
                        iterator.remove();
                        if (provided.realized) {
                            // With the collection that was wired, the provider may give a different one now
                            onRealized.execute(provided);
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

        // The composite store's iteratorNoFlush() reads the collections, so this asks them instead
        @Override
        public boolean containsRealized(Object element) {
            for (DomainObjectCollectionInternal<? extends T> collection : wiredCollections()) {
                if (collection.containsRealized(element)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsRealizedIncludingUnwired(Object element) {
            for (StoredCollection<T> stored : snapshot()) {
                if (((DomainObjectCollectionInternal<? extends T>) stored.getWithoutSideEffects()).containsRealizedIncludingUnwired(element)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void realizePending(Class<?> type) {
            realizePendingSubscribedBy(elementType -> elementType == null || type.isAssignableFrom(elementType));
        }

        @Override
        public boolean hasPendingElements() {
            for (StoredCollection<T> stored : store) {
                if (stored instanceof ProvidedCollection && !((ProvidedCollection<T>) stored).realized) {
                    return true;
                }
                if (((DomainObjectCollectionInternal<? extends T>) stored.get()).hasPendingElements()) {
                    return true;
                }
            }
            return false;
        }

        // Eager actions on the composite count as eager actions on the composited collections. Provided collections that are
        // not wired yet are left until they are read, then their pending elements are realized for these actions.
        // Like reads, this repeats when listeners may have added pending elements to collections that were done already.
        @Override
        public void realizePendingSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
            while (true) {
                List<StoredCollection<T>> collections = snapshot();
                int[] announcementsBefore = new int[collections.size()];
                for (int index = 0; index < collections.size(); index++) {
                    announcementsBefore[index] = collections.get(index).announcements;
                }
                for (StoredCollection<T> stored : collections) {
                    if (isWired(stored)) {
                        ((DomainObjectCollectionInternal<? extends T>) stored.get()).realizePendingSubscribedBy(subscriptions);
                    }
                }
                if (!announcedSince(collections, announcementsBefore) && !hasPendingElementsSubscribedBy(subscriptions)) {
                    return;
                }
            }
        }

        @Override
        public boolean hasPendingElementsSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
            for (StoredCollection<T> stored : store) {
                if (isWired(stored) && ((DomainObjectCollectionInternal<? extends T>) stored.get()).hasPendingElementsSubscribedBy(subscriptions)) {
                    return true;
                }
            }
            return false;
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
        @Nullable
        public List<T> removeRealized(ProviderInternal<? extends T> provider) {
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
        public ProviderInternal<Set<T>> getElements() {
            return new BuildableBackedProvider<>(
                context -> {
                    for (StoredCollection<T> collection : store) {
                        context.add(collection.get().getElements());
                    }
                },
                Cast.uncheckedCast(Set.class),
                this::collect
            );
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
