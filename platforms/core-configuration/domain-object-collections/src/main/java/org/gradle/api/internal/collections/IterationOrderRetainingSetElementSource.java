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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class IterationOrderRetainingSetElementSource<T> extends AbstractIterationOrderRetainingElementSource<T> {

    private static final Spec<ValuePointer<?>> NO_DUPLICATES = pointer -> !pointer.getElement().isDuplicate(pointer.getIndex());

    /**
     * Tracks the subset of values added with add() (without a Provider), allowing us to filter out duplicates
     * from that subset in constant time.
     */
    private Set<T> nonProvidedValues = Collections.emptySet();

    // Elements added with a provider. Elements removed with their provider are dropped when found.
    private List<Element<T>> providedElements = Collections.emptyList();

    private void ensureNonProviderValuesMutable() {
        if (nonProvidedValues == Collections.EMPTY_SET) {
            nonProvidedValues = new HashSet<T>();
        }
    }

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new NoDuplicatesIterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new NoDuplicatesIterator();
    }

    /**
     * Returns the number of elements iteration would produce, without realizing any pending element.
     * <p>
     * Realized elements already exclude their duplicates, and a pending single-element provider is cheaply
     * counted as one, so these contribute their own size.
     * A pending collection provider has to be queried to learn its size anyway, so its values are peeked and
     * only those not already present in the realized elements or in an earlier collection provider are counted,
     * just as realizing it would mark them as duplicates.
     * <p>
     * A pending single-element provider whose value is a duplicate is still counted until it is realized.
     */
    @Override
    public int size() {
        int count = 0;
        Set<T> seen = null;
        for (Element<T> element : getInserted()) {
            if (element.isRealized() || !element.isProvidedByCollection()) {
                count += element.size();
                continue;
            }
            if (seen == null) {
                seen = new HashSet<>();
                Iterators.addAll(seen, iteratorNoFlush());
            }
            for (T value : element.peekValues()) {
                if (seen.add(value)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean add(T element) {
        modCount++;
        ensureNonProviderValuesMutable();
        if (nonProvidedValues.add(element)) {
            getInserted().add(new Element<T>(element));
            if (isProvidedValue(element)) {
                // Provided values are deduplicated when they are realized, so a value realized earlier is checked here
                markDuplicates(element);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addRealized(T value) {
        // Announced even if another element shows an equal value already, as the one shown may be this one
        markDuplicates(value);
        return true;
    }

    @Override
    void addRealizedDuplicate(T value) {
        markDuplicates(value);
    }

    @Override
    protected void clearCachedElement(Element<T> element) {
        // Taken before clearing: reading the values afterwards would query the provider for the new ones
        List<T> previousValues = element.isRealized() ? element.getValues() : Collections.emptyList();
        super.clearCachedElement(element);
        for (T value : previousValues) {
            markDuplicates(value);
        }
    }

    @Override
    @Nullable
    public List<T> removeRealized(ProviderInternal<? extends T> provider) {
        List<T> removed = super.removeRealized(provider);
        if (removed == null || removed.isEmpty()) {
            return removed;
        }
        List<T> gone = new ArrayList<>(removed.size());
        for (T value : removed) {
            // A duplicate hidden by the removed value, e.g. one added directly, takes its place
            markDuplicates(value);
            if (!Iterators.contains(iteratorNoFlush(), value)) {
                gone.add(value);
            }
        }
        return gone;
    }

    @Override
    public boolean remove(Object o) {
        nonProvidedValues.remove(o);
        return super.remove(o);
    }

    @Override
    public void clear() {
        nonProvidedValues = Collections.emptySet();
        providedElements = Collections.emptyList();
        super.clear();
    }

    // The removed value takes the equal values hidden as its duplicates with it, otherwise they would show up again later
    private class NoDuplicatesIterator extends RealizedElementCollectionIterator {
        NoDuplicatesIterator() {
            super(getInserted(), NO_DUPLICATES);
        }

        @Override
        public void remove() {
            int index = previousIndex;
            int subIndex = previousSubIndex;
            if (index < 0) {
                super.remove();
                return;
            }
            Element<T> current = backingList.get(index);
            T value = current.getValues().get(subIndex);
            super.remove();
            nonProvidedValues.remove(value);
            if (!providedElements.isEmpty() && removeHiddenDuplicates(value, index)) {
                boolean currentKept = index < backingList.size() && backingList.get(index) == current;
                nextIndex = index;
                nextSubIndex = currentKept ? subIndex - 1 : -1;
                expectedModCount = modCount;
                updateNext();
            }
        }
    }

    // Hidden duplicates always come after the visible value, so only elements from its position on are searched
    private boolean removeHiddenDuplicates(T value, int fromIndex) {
        boolean removed = false;
        List<Element<T>> inserted = getInserted();
        int index = fromIndex;
        while (index < inserted.size()) {
            Element<T> element = inserted.get(index);
            boolean removedFromElement = false;
            if (element.isRealized()) {
                removedFromElement = element.removeAnnounced(value);
            }
            if (removedFromElement && element.getValues().isEmpty() && !element.isProvided()) {
                inserted.remove(index);
                modCount++;
            } else {
                index++;
            }
            removed |= removedFromElement;
        }
        return removed;
    }

    private void addProvidedElement(Element<T> element) {
        if (providedElements.isEmpty()) {
            providedElements = new ArrayList<>();
        }
        providedElements.add(element);
        addPendingElement(element);
    }

    private boolean isProvidedValue(T value) {
        Iterator<Element<T>> iterator = providedElements.iterator();
        while (iterator.hasNext()) {
            Element<T> element = iterator.next();
            if (element.isDetached()) {
                iterator.remove();
            } else if (element.isRealized() && element.getValues().contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAddedBy(ProviderInternal<?> provider) {
        for (Element<T> element : getInserted()) {
            if (element.isProvidedBy(provider)) {
                return true;
            }
        }
        return false;
    }

    // Values that are not announced yet don't count
    private void markDuplicates(T value) {
        boolean seen = false;
        for (Element<T> element : getInserted()) {
            if (element.isRealized()) {
                List<T> collected = element.getValues();
                for (int index = 0; index < collected.size(); index++) {
                    if (Objects.equal(collected.get(index), value) && element.isAnnounced(index)) {
                        if (seen) {
                            element.setDuplicate(index);
                        } else {
                            // The occurrence that was first may be gone, e.g. when a provider value changed
                            element.clearDuplicate(index);
                            seen = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean addPending(ProviderInternal<? extends T> provider) {
        modCount++;
        // Compared by provider: an element realized on add holds its values, so it is not equal to a new one
        if (isAddedBy(provider)) {
            return false;
        }
        addProvidedElement(cachingElement(provider));
        return true;
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        modCount++;
        // Compared by provider: an element realized on add holds its values, so it is not equal to a new one
        if (isAddedBy(provider)) {
            return false;
        }
        addProvidedElement(cachingElement(provider));
        return true;
    }

    @Override
    public ProviderInternal<Set<T>> getElements() {
        return new BuildableBackedProvider<>(
            this,
            Cast.uncheckedCast(Set.class),
            SerializableLambdas.factory(() -> {
                ImmutableSet.Builder<T> builder = ImmutableSet.builderWithExpectedSize(estimatedSize());
                builder.addAll(iterator());
                return builder.build();
            })
        );
    }

}
