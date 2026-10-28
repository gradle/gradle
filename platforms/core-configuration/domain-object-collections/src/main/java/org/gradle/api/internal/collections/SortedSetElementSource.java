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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.internal.DefaultMutationGuard;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.Collectors;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

public class SortedSetElementSource<T> implements ElementSource<T> {
    private final TreeSet<T> values;
    // Note the juggling of pending is a memory optimization to save retained LinkedHashSets
    // Each DomainObjectSet has a pending set and a Configuration has several DomainObjectSets
    // And a Project has many Configurations.
    private Set<Collectors.TypedCollector<T>> pending = Collections.emptySet();
    // Realized collectors are retained with the values they captured, so the producers of these values remain
    // build dependencies of getElements(), and the values can be removed by provider without querying it again
    private Map<Collectors.TypedCollector<T>, RealizedValues<T>> realized = Collections.emptyMap();
    // Values added while a provider is queried, as providers that create the elements, like register(), add them themselves
    @Nullable
    private List<T> addedWhileCollecting;
    private Action<T> addRealizedAction;
    private EventSubscriptionVerifier<T> subscriptionVerifier = type -> false;
    private final MutationGuard lazyGuard = new DefaultMutationGuard();

    public SortedSetElementSource(Comparator<T> comparator) {
        this.values = new TreeSet<T>(comparator);
    }

    @Override
    public boolean isEmpty() {
        if (!values.isEmpty()) {
            return false;
        }
        for (Collectors.TypedCollector<T> collector : pending) {
            if (collector.size() != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return values.isEmpty() && pending.isEmpty();
    }

    /**
     * Returns the number of elements iteration would produce, without realizing any pending element.
     * <p>
     * A pending single-element provider is cheaply counted as one.
     * A pending collection provider has to be queried to learn its size anyway, so its values are collected and
     * only those not already present in {@link #values} or in an earlier collection provider are counted,
     * just as adding them to the sorted set on realization would drop them.
     * <p>
     * A pending single-element provider whose value is a duplicate is still counted until it is realized.
     */
    @Override
    public int size() {
        int count = values.size();
        Set<T> pendingValues = null;
        for (Collectors.TypedCollector<T> collector : pending) {
            if (!(collector instanceof CollectionProviderCollector)) {
                count += collector.size();
                continue;
            }
            if (pendingValues == null) {
                pendingValues = new TreeSet<>(values.comparator());
            }
            for (T value : collectValues(collector)) {
                if (!values.contains(value) && pendingValues.add(value)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static <T> List<T> collectValues(Collectors.TypedCollector<T> collector) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        collector.collectInto(builder);
        return builder.build();
    }

    @Override
    public int estimatedSize() {
        int pendingSize = 0;
        for (Collectors.TypedCollector<T> collector : pending) {
            pendingSize += collector.size();
        }

        return values.size() + pendingSize;
    }

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new ValuesIterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new ValuesIterator();
    }

    // Values removed through the iterator are forgotten by the realized collectors as well, like with remove(Object)
    private class ValuesIterator implements Iterator<T> {
        private final Iterator<T> delegate = values.iterator();
        @Nullable
        private T last;

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public T next() {
            last = delegate.next();
            return last;
        }

        @Override
        public void remove() {
            delegate.remove();
            forgetRealized(last);
        }
    }

    @Override
    public boolean contains(Object element) {
        realizePending();
        return values.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        realizePending();
        return values.containsAll(elements);
    }

    @Override
    public boolean add(T element) {
        boolean added = values.add(element);
        if (added && addedWhileCollecting != null) {
            addedWhileCollecting.add(element);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        if (values.remove(o)) {
            forgetRealized(o);
            return true;
        }
        return false;
    }

    // The collectors stay, so adding their providers again has no effect
    private void forgetRealized(Object value) {
        for (RealizedValues<T> realizedValues : realized.values()) {
            realizedValues.forget(candidate -> isEquivalent(candidate, value));
        }
    }

    @Override
    public void clear() {
        for (RealizedValues<T> realizedValues : realized.values()) {
            realizedValues.markRemoved();
        }
        pending = Collections.emptySet();
        realized = Collections.emptyMap();
        values.clear();
    }

    @Override
    public boolean hasPendingElements() {
        return !pending.isEmpty();
    }

    @Override
    public boolean hasPendingElements(Class<?> type) {
        for (Collectors.TypedCollector<T> collector : pending) {
            if (collector.getType() == null || type.isAssignableFrom(collector.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void realizePending() {
        realizePendingMatching(collector -> true);
    }

    @Override
    public void realizePending(Class<?> type) {
        realizePendingMatching(collector -> collector.getType() == null || type.isAssignableFrom(collector.getType()));
    }

    @Override
    public boolean hasPendingElementsSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
        for (Collectors.TypedCollector<T> collector : pending) {
            if (subscriptions.isSubscribed(collector.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void realizePendingSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
        realizePendingMatching(collector -> subscriptions.isSubscribed(collector.getType()));
    }

    // Listeners may add pending elements while others are realized, so this repeats until none of them match
    private void realizePendingMatching(Predicate<Collectors.TypedCollector<T>> condition) {
        while (!pending.isEmpty()) {
            List<Collectors.TypedCollector<T>> matching = new ArrayList<>();
            for (Collectors.TypedCollector<T> collector : pending) {
                if (condition.test(collector)) {
                    matching.add(collector);
                }
            }
            if (matching.isEmpty()) {
                return;
            }
            realize(matching);
        }
    }

    private void realize(Iterable<Collectors.TypedCollector<T>> collectors) {
        for (Collectors.TypedCollector<T> collector : collectors) {
            if (!pending.remove(collector)) {
                // Realized or removed meanwhile, e.g. by a listener or through the provider itself
                continue;
            }
            ImmutableList.Builder<T> builder = ImmutableList.builder();
            List<T> previouslyCollecting = addedWhileCollecting;
            List<T> addedByProvider = new ArrayList<>();
            addedWhileCollecting = addedByProvider;
            try {
                // Collect elements discarding potential side effects aggregated in the returned value
                collector.collectInto(builder);
            } finally {
                addedWhileCollecting = previouslyCollecting;
            }
            List<T> collected = builder.build();
            if (realized == Collections.EMPTY_MAP) {
                realized = new LinkedHashMap<>();
            }
            RealizedValues<T> realizedValues = new RealizedValues<>(collected);
            for (T value : addedByProvider) {
                if (collected.stream().anyMatch(candidate -> isEquivalent(candidate, value))) {
                    realizedValues.addAdded(value);
                }
            }
            realized.put(collector, realizedValues);
            realizedValues.announcedCount = 0;
            try {
                for (T element : collected) {
                    if (realizedValues.removed) {
                        // A listener removed the provider or cleared the collection
                        break;
                    }
                    realizedValues.announcedCount++;
                    if (values.add(element)) {
                        realizedValues.addAdded(element);
                        if (addRealizedAction != null) {
                            addRealizedAction.execute(element);
                        }
                    }
                }
            } finally {
                realizedValues.announcedCount = -1;
            }
        }
    }

    @Override
    public boolean addPending(final ProviderInternal<? extends T> provider) {
        Collectors.TypedCollector<T> collector = collectorFromProvider(provider);
        if (realized.containsKey(collector)) {
            return false;
        }
        ensurePendingIsMutable();
        if (provider instanceof ChangingValue) {
            Cast.<ChangingValue<T>>uncheckedNonnullCast(provider).onValueChange(SerializableLambdas.action(previousValue -> {
                remove(previousValue);
                pending.add(collectorFromProvider(provider));
            }));
        }

        boolean added = pending.add(collector);
        // TODO: We likely want to also immediately realize ChangingValue providers in the
        //  onValueChange callback above.
        if (subscriptionVerifier.isSubscribed(provider.getType())) {
            realize(Collections.singleton(collector));
        }
        return added;
    }

    private void ensurePendingIsMutable() {
        if (pending == Collections.EMPTY_SET) {
            pending = new LinkedHashSet<>();
        }
    }

    private Collectors.TypedCollector<T> collectorFromProvider(final ProviderInternal<? extends T> provider) {
        return new Collectors.TypedCollector<>(provider.getType(), new Collectors.ElementFromProvider<>(provider));
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        return removeByProvider(provider);
    }

    @Override
    @Nullable
    public List<T> removeRealized(ProviderInternal<? extends T> provider) {
        Iterator<Map.Entry<Collectors.TypedCollector<T>, RealizedValues<T>>> iterator = realized.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Collectors.TypedCollector<T>, RealizedValues<T>> entry = iterator.next();
            if (entry.getKey().isProvidedBy(provider)) {
                iterator.remove();
                RealizedValues<T> realizedValues = entry.getValue();
                if (realizedValues.external || realizedValues.captured.isEmpty()) {
                    // Nothing known to remove: either the provider added its element itself, or its values were removed already
                    return null;
                }
                realizedValues.markRemoved();
                // Only values that the provider added are removed: an equal value that was there before stays
                List<T> removed = new ArrayList<>(realizedValues.added.size());
                for (T value : realizedValues.added) {
                    RealizedValues<T> otherCapturing = findCapturing(value);
                    if (otherCapturing != null) {
                        otherCapturing.addAdded(value);
                    } else if (values.remove(value)) {
                        removed.add(value);
                    }
                }
                return removed;
            }
        }
        return null;
    }

    @Nullable
    private RealizedValues<T> findCapturing(T value) {
        for (RealizedValues<T> realizedValues : realized.values()) {
            List<T> captured = realizedValues.captured;
            for (int index = 0; index < captured.size(); index++) {
                // A value that is not announced yet is not in the collection
                if (isEquivalent(captured.get(index), value) && realizedValues.isAnnounced(index)) {
                    return realizedValues;
                }
            }
        }
        return null;
    }

    // The same equality as the one of the values
    private boolean isEquivalent(T candidate, Object value) {
        Comparator<? super T> comparator = values.comparator();
        if (comparator == null) {
            return Cast.<Comparable<Object>>uncheckedNonnullCast(candidate).compareTo(value) == 0;
        }
        return comparator.compare(candidate, Cast.uncheckedNonnullCast(value)) == 0;
    }

    private boolean removeByProvider(ProviderInternal<?> provider) {
        return removePendingCollector(provider) != null;
    }

    private Collectors.@Nullable TypedCollector<T> removePendingCollector(ProviderInternal<?> provider) {
        Iterator<Collectors.TypedCollector<T>> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Collectors.TypedCollector<T> collector = iterator.next();
            if (collector.isProvidedBy(provider)) {
                iterator.remove();
                return collector;
            }
        }
        return null;
    }

    @Override
    public boolean addPendingCollection(final CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        Collectors.TypedCollector<T> collector = collectorFromCollectionProvider(provider);
        if (realized.containsKey(collector)) {
            return false;
        }
        ensurePendingIsMutable();
        if (provider instanceof ChangingValue) {
            Cast.<ChangingValue<Iterable<T>>>uncheckedNonnullCast(provider).onValueChange(SerializableLambdas.action(previousValues -> {
                for (T value : previousValues) {
                    remove(value);
                }
                pending.add(collectorFromCollectionProvider(provider));
            }));
        }

        boolean added = pending.add(collector);
        // TODO: We likely want to also immediately realize ChangingValue providers in the
        //  onValueChange callback above.
        if (subscriptionVerifier.isSubscribed(provider.getElementType())) {
            realize(Collections.singleton(collector));
        }
        return added;
    }

    private Collectors.TypedCollector<T> collectorFromCollectionProvider(final CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return new CollectionProviderCollector<>(provider);
    }

    @Override
    public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return removeByProvider(provider);
    }

    @Override
    public ProviderInternal<Set<T>> getElements() {
        return new BuildableBackedProvider<>(
            context -> {
                for (Map.Entry<Collectors.TypedCollector<T>, RealizedValues<T>> entry : realized.entrySet()) {
                    // Values removed by value take their producer with them
                    if (entry.getValue().external || !entry.getValue().captured.isEmpty()) {
                        context.add(entry.getKey().getProducer());
                    }
                }
                for (Collectors.TypedCollector<T> collector : pending) {
                    context.add(collector.getProducer());
                }
            },
            Cast.uncheckedCast(Set.class),
            SerializableLambdas.factory(() -> {
                realizePending();
                return ImmutableSet.copyOf(values);
            })
        );
    }

    @Override
    public void onPendingAdded(Action<T> action) {
        this.addRealizedAction = action;
    }

    @Override
    public void setSubscriptionVerifier(EventSubscriptionVerifier<T> subscriptionVerifier) {
        this.subscriptionVerifier = subscriptionVerifier;
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {
        Collectors.TypedCollector<T> collector = removePendingCollector(provider);
        if (collector != null) {
            // The provider added its element itself, so its value is not known here. It is retained like the others,
            // so adding it again has no effect, while removing it falls back to querying it.
            if (realized == Collections.EMPTY_MAP) {
                realized = new LinkedHashMap<>();
            }
            realized.put(collector, RealizedValues.external());
        }
    }

    @Override
    public MutationGuard getLazyBehaviorGuard() {
        return lazyGuard;
    }

    private static final class RealizedValues<T> {
        private static final RealizedValues<?> EXTERNAL = new RealizedValues<>(Collections.emptyList(), true);

        // Values are rarely forgotten, so the captured list is only copied then
        private List<T> captured;
        // The captured values that were not in the collection yet
        private List<T> added = Collections.emptyList();
        private final boolean external;
        // Set when the provider is removed or the collection is cleared, possibly while its values are announced
        private boolean removed;
        // While the values are announced: how many of the captured ones were announced. These are always the first ones,
        // as values are announced in order and only announced ones are forgotten. Negative otherwise.
        private int announcedCount = -1;

        RealizedValues(List<T> captured) {
            this(captured, false);
        }

        private RealizedValues(List<T> captured, boolean external) {
            this.captured = captured;
            this.external = external;
        }

        static <T> RealizedValues<T> external() {
            return Cast.uncheckedNonnullCast(EXTERNAL);
        }

        void addAdded(T value) {
            if (added == Collections.EMPTY_LIST) {
                added = new ArrayList<>(captured.size());
            }
            added.add(value);
        }

        boolean isAnnounced(int index) {
            return announcedCount < 0 || index < announcedCount;
        }

        void forget(Predicate<T> matching) {
            if (!added.isEmpty()) {
                added.removeIf(matching);
            }
            // Values not announced yet are not in the collection, so removing an equal one doesn't concern them
            List<T> remaining = null;
            int remainingAnnounced = announcedCount;
            for (int index = 0; index < captured.size(); index++) {
                T candidate = captured.get(index);
                if (isAnnounced(index) && matching.test(candidate)) {
                    if (remaining == null) {
                        remaining = new ArrayList<>(captured.subList(0, index));
                    }
                    if (announcedCount >= 0) {
                        remainingAnnounced--;
                    }
                } else if (remaining != null) {
                    remaining.add(candidate);
                }
            }
            if (remaining != null) {
                captured = remaining;
                announcedCount = remainingAnnounced;
            }
        }

        void markRemoved() {
            // The external marker is shared, and its provider has no values to announce
            if (!external) {
                removed = true;
            }
        }
    }

    // Unlike single element providers, the size of these is only known by querying the provider
    private static class CollectionProviderCollector<T> extends Collectors.TypedCollector<T> {
        CollectionProviderCollector(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            super(provider.getElementType(), new Collectors.ElementsFromCollectionProvider<>(provider));
        }
    }
}
