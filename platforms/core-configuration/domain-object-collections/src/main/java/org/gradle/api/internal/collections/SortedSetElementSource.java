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
import com.google.common.collect.Lists;
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
        pending = Collections.emptySet();
        realized = Collections.emptyMap();
        values.clear();
    }

    @Override
    public void realizePending() {
        if (!pending.isEmpty()) {
            List<Collectors.TypedCollector<T>> copied = Lists.newArrayList(pending);
            realize(copied);
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        if (!pending.isEmpty()) {
            List<Collectors.TypedCollector<T>> copied = new ArrayList<>();
            for (Collectors.TypedCollector<T> collector : pending) {
                if (collector.getType() == null || type.isAssignableFrom(collector.getType())) {
                    copied.add(collector);
                }
            }
            realize(copied);
        }
    }

    private void realize(Iterable<Collectors.TypedCollector<T>> collectors) {
        for (Collectors.TypedCollector<T> collector : collectors) {
            pending.remove(collector);
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
            for (T element : collected) {
                if (values.add(element)) {
                    realizedValues.addAdded(element);
                    if (addRealizedAction != null) {
                        addRealizedAction.execute(element);
                    }
                }
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
        realizeIfSubscribed(collector, provider.getType());
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
            for (T candidate : realizedValues.captured) {
                if (isEquivalent(candidate, value)) {
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
        realizeIfSubscribed(collector, provider.getElementType());
        return added;
    }

    private void realizeIfSubscribed(Collectors.TypedCollector<T> collector, @Nullable Class<? extends T> type) {
        if (!subscriptionVerifier.isSubscribed(type)) {
            return;
        }
        realize(Collections.singleton(collector));
        if (!subscriptionVerifier.isSubscribedByListener(type)) {
            // Only realized to be forwarded to a composite collection, so the provider is queried again when this collection is read
            realized.remove(collector);
            pending.add(collector);
        }
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
                for (Collectors.TypedCollector<T> collector : realized.keySet()) {
                    context.add(collector.getProducer());
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

        void forget(Predicate<T> matching) {
            if (!added.isEmpty()) {
                added.removeIf(matching);
            }
            if (captured.stream().anyMatch(matching)) {
                List<T> remaining = new ArrayList<>(captured);
                remaining.removeIf(matching);
                captured = remaining;
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
