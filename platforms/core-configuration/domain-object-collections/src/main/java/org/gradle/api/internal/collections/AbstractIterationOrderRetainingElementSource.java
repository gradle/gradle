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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.Action;
import org.gradle.api.internal.DefaultMutationGuard;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.Collector;
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.internal.provider.Collectors.TypedCollector;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

abstract public class AbstractIterationOrderRetainingElementSource<T> implements ElementSource<T>, TaskDependencyContainer {

    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final List<Element<T>> inserted = new ArrayList<>();

    private final MutationGuard lazyGuard = new DefaultMutationGuard();

    private Action<T> pendingAddedAction;
    private EventSubscriptionVerifier<T> subscriptionVerifier = type -> false;

    protected int modCount;

    List<Element<T>> getInserted() {
        return inserted;
    }

    @Override
    public boolean isEmpty() {
        for (Element<T> element : inserted) {
            if (element.size() != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public int size() {
        return estimatedSize();
    }

    @Override
    public int estimatedSize() {
        int count = 0;
        for (Element<T> element : inserted) {
            count += element.size();
        }
        return count;
    }

    @Override
    public boolean contains(Object element) {
        return Iterators.contains(iterator(), element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        for (Object e : elements) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        Iterator<T> iterator = iteratorNoFlush();
        while (iterator.hasNext()) {
            T value = iterator.next();
            if (value.equals(o)) {
                iterator.remove();
                modCount++;
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        modCount++;
        for (Element<T> element : inserted) {
            element.detach();
        }
        inserted.clear();
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {

    }

    @Override
    public void realizePending() {
        realizePendingMatching(element -> true);
    }

    @Override
    public void realizePending(Class<?> type) {
        realizePendingMatching(element -> element.getType() == null || type.isAssignableFrom(element.getType()));
    }

    @Override
    public boolean hasPendingElements() {
        return hasPendingElements(Object.class);
    }

    @Override
    public boolean hasPendingElements(Class<?> type) {
        for (Element<T> element : inserted) {
            if (!element.isRealized() && (element.getType() == null || type.isAssignableFrom(element.getType()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasPendingElementsSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
        for (Element<T> element : inserted) {
            if (!element.isRealized() && subscriptions.isSubscribed(element.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void realizePendingSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions) {
        realizePendingMatching(element -> subscriptions.isSubscribed(element.getType()));
    }

    // Realizing an element notifies listeners, which may change this source, e.g. listeners of a composite
    // that elements are forwarded to. So the elements are collected first, and realized while they are still in it.
    // Listeners may also add pending elements, so this repeats until none of them match.
    private void realizePendingMatching(Predicate<Element<T>> condition) {
        List<Element<T>> pending = collectPending(condition);
        while (pending != null) {
            realizeStillInserted(pending);
            pending = collectPending(condition);
        }
    }

    @Nullable
    private List<Element<T>> collectPending(Predicate<Element<T>> condition) {
        List<Element<T>> pending = null;
        for (Element<T> element : inserted) {
            if (!element.isRealized() && condition.test(element)) {
                if (pending == null) {
                    pending = new ArrayList<>();
                }
                pending.add(element);
            }
        }
        return pending;
    }

    private void realizeStillInserted(List<Element<T>> pending) {
        int expectedModCount = modCount;
        boolean changedByListeners = false;
        for (Element<T> element : pending) {
            changedByListeners |= modCount != expectedModCount;
            if (element.isRealized() || (changedByListeners && !containsElement(element))) {
                continue;
            }
            modCount++;
            expectedModCount = modCount;
            element.realize();
        }
    }

    private boolean containsElement(Element<T> element) {
        for (Element<T> candidate : inserted) {
            if (candidate == element) {
                return true;
            }
        }
        return false;
    }

    protected void clearCachedElement(Element<T> element) {
        modCount++;
        element.clearCache();
    }

    Element<T> cachingElement(ProviderInternal<? extends T> provider) {
        final Element<T> element = new Element<>(provider.getType(), new ElementFromProvider<>(provider), SerializableLambdas.action(this::doAddRealized), SerializableLambdas.action(this::addRealizedDuplicate));
        if (provider instanceof ChangingValue) {
            Cast.<ChangingValue<T>>uncheckedNonnullCast(provider).onValueChange(SerializableLambdas.action(previousValue -> clearCachedElement(element)));
        }
        return element;
    }

    Element<T> cachingElement(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        final Element<T> element = new Element<>(provider.getElementType(), new ElementsFromCollectionProvider<>(provider), SerializableLambdas.action(this::doAddRealized), SerializableLambdas.action(this::addRealizedDuplicate));
        if (provider instanceof ChangingValue) {
            Cast.<ChangingValue<Iterable<T>>>uncheckedNonnullCast(provider).onValueChange(SerializableLambdas.action(previousValues -> clearCachedElement(element)));
        }
        return element;
    }

    private void doAddRealized(T value) {
        if (addRealized(value) && pendingAddedAction != null) {
            pendingAddedAction.execute(value);
        }
    }

    abstract boolean addRealized(T element);

    /**
     * Called instead of {@link #addRealized(Object)} for a value equal to one the element announced already, which is not announced again.
     */
    void addRealizedDuplicate(T value) {
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        return removeByProvider(provider);
    }

    private boolean removeByProvider(ProviderInternal<?> provider) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<T> next = iterator.next();
            if (!next.isRealized() && next.isProvidedBy(provider)) {
                modCount++;
                iterator.remove();
                next.detach();
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public List<T> removeRealized(ProviderInternal<? extends T> provider) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<T> next = iterator.next();
            if (next.isRealized() && next.isProvidedBy(provider)) {
                modCount++;
                iterator.remove();
                next.detach();
                if (next.isEmptiedByRemoval()) {
                    // Its values were removed already, so removing it falls back to querying it, as for other providers
                    return null;
                }
                return next.getVisibleValues();
            }
        }
        return null;
    }

    @Override
    public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        return removeByProvider(provider);
    }

    @Override
    public void onPendingAdded(final Action<T> action) {
        this.pendingAddedAction = action;
    }

    @Override
    public void setSubscriptionVerifier(EventSubscriptionVerifier<T> subscriptionVerifier) {
        this.subscriptionVerifier = subscriptionVerifier;
    }

    protected boolean addPendingElement(Element<T> element) {
        boolean added = inserted.add(element);
        if (subscriptionVerifier.isSubscribed(element.getType())) {
            element.realize();
        }
        return added;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        for (Element<T> element : inserted) {
            if (!element.isEmptiedByRemoval()) {
                context.add(element.getProducer());
            }
        }
    }

    @Override
    public MutationGuard getLazyBehaviorGuard() {
        return lazyGuard;
    }

    protected class RealizedElementCollectionIterator implements Iterator<T> {
        final List<Element<T>> backingList;
        final Spec<ValuePointer<?>> acceptanceSpec;
        int nextIndex = -1;
        int nextSubIndex = -1;
        int previousIndex = -1;
        int previousSubIndex = -1;
        T next;
        int expectedModCount = modCount;

        RealizedElementCollectionIterator(List<Element<T>> backingList, Spec<ValuePointer<?>> acceptanceSpec) {
            this.backingList = backingList;
            this.acceptanceSpec = acceptanceSpec;
            updateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        void updateNext() {
            if (nextIndex == -1) {
                nextIndex = 0;
            }

            int i = nextIndex;
            while (i < backingList.size()) {
                Element<T> candidate = backingList.get(i);
                if (candidate.isRealized()) {
                    List<T> collected = candidate.getValues();
                    int j = nextSubIndex + 1;
                    while (j < collected.size()) {
                        T value = collected.get(j);
                        if (accepts(candidate, j)) {
                            nextIndex = i;
                            nextSubIndex = j;
                            next = value;
                            return;
                        }
                        j++;
                    }
                    nextSubIndex = -1;
                }
                i++;
            }
            nextIndex = i;
            next = null;
        }

        // Values of an element that is being realized become visible when they are announced
        boolean accepts(Element<T> candidate, int index) {
            return candidate.isAnnounced(index) && acceptanceSpec.isSatisfiedBy(new ValuePointer<T>(candidate, index));
        }

        @Override
        public T next() {
            checkForComodification();
            if (next == null) {
                throw new NoSuchElementException();
            }
            T thisNext = next;
            previousIndex = nextIndex;
            previousSubIndex = nextSubIndex;
            updateNext();
            return thisNext;
        }

        @Override
        public void remove() {
            if (previousIndex > -1) {
                checkForComodification();
                Element<T> element = backingList.get(previousIndex);
                List<T> collected = element.getValues();
                if (collected.size() > 1) {
                    element.remove(collected.get(previousSubIndex));
                    // The next value may be in a later element, e.g. when the rest of this one is not announced yet
                    if (nextIndex == previousIndex) {
                        nextSubIndex--;
                    }
                } else if (element.isProvided()) {
                    // Kept, see isEmptiedByRemoval(); the next value is in a later element
                    element.remove(collected.get(previousSubIndex));
                } else {
                    backingList.remove(previousIndex);
                    nextIndex--;
                }
                previousIndex = -1;
                previousSubIndex = -1;
            } else {
                throw new IllegalStateException();
            }
        }

        final void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    protected static class Element<T> extends TypedCollector<T> {
        private List<T> cache = null;
        private List<T> removedValues = null;
        private IntSet duplicates = IntSets.emptySet();
        private boolean realized;
        private int removals;
        // While the element is realized: how many of its values were announced. These are always the first ones,
        // as values are announced in order and removing one of them moves the ones after it back. Negative otherwise.
        private int announcedCount = -1;
        private boolean detached;

        private final Action<T> realizeAction;
        @Nullable
        private final Action<T> duplicateAction;

        Element(@Nullable Class<? extends T> type, Collector<T> delegate, Action<T> realizeAction, Action<T> duplicateAction) {
            super(type, delegate);
            this.realizeAction = realizeAction;
            this.duplicateAction = duplicateAction;
        }

        Element(T value) {
            super(null, new SingleElement<T>(value));
            this.realizeAction = null;
            this.duplicateAction = null;
            realize();
        }

        public boolean isRealized() {
            return realized;
        }

        boolean isProvidedByCollection() {
            return delegate instanceof ElementsFromCollectionProvider;
        }

        List<T> peekValues() {
            ImmutableList.Builder<T> builder = ImmutableList.builder();
            super.collectInto(builder);
            List<T> values = new ArrayList<>(builder.build());
            if (removedValues != null) {
                values.removeAll(removedValues);
            }
            return values;
        }

        @Override
        public int size() {
            if (!realized) {
                return super.size();
            }
            return announcedCount < 0 ? cache.size() - duplicates.size() : getVisibleValues().size();
        }

        public void realize() {
            if (cache == null) {
                ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(delegate.size());
                // Collect elements discarding potential side effects aggregated in the returned value
                super.collectInto(builder);
                cache = new ArrayList<>(builder.build());
                if (removedValues != null) {
                    cache.removeAll(removedValues);
                }
                realized = true;
                if (realizeAction != null) {
                    announcedCount = 0;
                    Set<T> notified = new HashSet<>();
                    try {
                        // Listeners may remove values of this element, or the element itself, while it is realized
                        while (!detached && cache != null && announcedCount < cache.size()) {
                            T value = cache.get(announcedCount);
                            announcedCount++;
                            // An equal value that was announced may have been removed meanwhile, then this one takes its place
                            if (notified.add(value) || !hasOtherAnnounced(value)) {
                                realizeAction.execute(value);
                            } else if (duplicateAction != null) {
                                duplicateAction.execute(value);
                            }
                        }
                    } finally {
                        announcedCount = -1;
                    }
                }
            }
        }

        @Override
        public void collectInto(ImmutableCollection.Builder<T> builder) {
            if (!realized) {
                realize();
            }
            builder.addAll(cache);
        }

        List<T> getValues() {
            if (!realized) {
                realize();
            }
            return cache;
        }

        public boolean remove(T value) {
            if (cache != null) {
                int index = cache.indexOf(value);
                if (index < 0) {
                    rememberRemoved(value);
                    return false;
                }
                removeAt(index);
                return true;
            }
            rememberRemoved(value);
            return true;
        }

        private void removeAt(int index) {
            rememberRemoved(cache.get(index));
            cache.remove(index);
            if (index < announcedCount) {
                announcedCount--;
            }
            removals++;
            shiftDuplicatesAfterRemovalOf(index);
        }

        private void rememberRemoved(T value) {
            if (removedValues == null) {
                removedValues = new ArrayList<>();
            }
            removedValues.add(value);
        }

        // Values that are not announced yet are not in the collection, so they stay
        boolean removeAnnounced(T value) {
            boolean removed = false;
            for (int index = cache.size() - 1; index >= 0; index--) {
                if (Objects.equal(cache.get(index), value) && isAnnounced(index)) {
                    removeAt(index);
                    removed = true;
                }
            }
            return removed;
        }

        boolean isProvided() {
            return realizeAction != null;
        }

        // A provided element whose values were all removed stays, so adding its provider again is known to have no effect
        boolean isEmptiedByRemoval() {
            return realized && removals > 0 && cache.isEmpty();
        }

        // The value being announced counts as announced already
        private boolean hasOtherAnnounced(T value) {
            int announcedOccurrences = 0;
            for (int index = 0; index < cache.size(); index++) {
                if (Objects.equal(cache.get(index), value) && isAnnounced(index) && ++announcedOccurrences > 1) {
                    return true;
                }
            }
            return false;
        }

        boolean isAnnounced(int index) {
            return announcedCount < 0 || index < announcedCount;
        }

        void detach() {
            detached = true;
        }

        boolean isDetached() {
            return detached;
        }

        // Values of an element that is still being realized are visible once they are announced
        List<T> getVisibleValues() {
            if (duplicates.isEmpty() && announcedCount < 0) {
                return cache;
            }
            List<T> visible = new ArrayList<>(cache.size());
            for (int index = 0; index < cache.size(); index++) {
                T value = cache.get(index);
                if (!duplicates.contains(index) && isAnnounced(index)) {
                    visible.add(value);
                }
            }
            return visible;
        }

        // Duplicates are tracked by index, so the ones after the removed value move one position back
        private void shiftDuplicatesAfterRemovalOf(int removedIndex) {
            if (duplicates.isEmpty()) {
                return;
            }
            IntSet shifted = new IntOpenHashSet(duplicates.size());
            IntIterator iterator = duplicates.iterator();
            while (iterator.hasNext()) {
                int index = iterator.nextInt();
                if (index < removedIndex) {
                    shifted.add(index);
                } else if (index > removedIndex) {
                    shifted.add(index - 1);
                }
            }
            duplicates = shifted;
        }

        boolean isDuplicate(int index) {
            return duplicates.contains(index);
        }

        void setDuplicate(int index) {
            if (duplicates == IntSets.EMPTY_SET) {
                duplicates = new IntOpenHashSet(1);
            }
            duplicates.add(index);
        }

        void clearDuplicate(int index) {
            if (!duplicates.isEmpty()) {
                duplicates.remove(index);
            }
        }

        void clearCache() {
            cache = null;
            realized = false;
            duplicates = IntSets.emptySet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Element<?> that = (Element<?>) o;
            return Objects.equal(delegate, that.delegate) &&
                Objects.equal(cache, that.cache);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate, cache);
        }
    }

    protected static class ValuePointer<T> {
        private final Element<T> element;
        private final Integer index;

        public ValuePointer(Element<T> element, Integer index) {
            this.element = element;
            this.index = index;
        }

        public Element<T> getElement() {
            return element;
        }

        public Integer getIndex() {
            return index;
        }
    }

}
