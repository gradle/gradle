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

    // Values of providers realized on add for an eager listener, which a value added directly can be a duplicate of.
    // Values removed since may still be in here, which only costs a needless check for duplicates.
    private Set<T> valuesRealizedOnAdd = Collections.emptySet();

    private void ensureNonProviderValuesMutable() {
        if (nonProvidedValues == Collections.EMPTY_SET) {
            nonProvidedValues = new HashSet<T>();
        }
    }

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new RealizedElementCollectionIterator(getInserted(), NO_DUPLICATES);
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new RealizedElementCollectionIterator(getInserted(), NO_DUPLICATES);
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
            if (valuesRealizedOnAdd.contains(element)) {
                markDuplicates(element);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addRealized(T value) {
        markDuplicates(value);
        return true;
    }

    @Override
    protected void clearCachedElement(Element<T> element) {
        List<T> previousValues = element.isRealized() ? element.getValues() : null;
        super.clearCachedElement(element);
        if (previousValues != null) {
            for (T value : element.getValues()) {
                markDuplicates(value);
            }
            // A value hidden as a duplicate of a previous value may be visible again
            for (T value : previousValues) {
                markDuplicates(value);
            }
        }
    }

    @Override
    @Nullable
    public List<T> removeRealized(ProviderInternal<? extends T> provider) {
        List<T> removed = super.removeRealized(provider);
        if (removed == null) {
            return null;
        }
        List<T> gone = new ArrayList<>(removed.size());
        for (T value : removed) {
            // An equal value hidden as a duplicate of the removed one takes its place
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
        valuesRealizedOnAdd = Collections.emptySet();
        super.clear();
    }

    private void markDuplicates(T value) {
        boolean seen = false;
        for (Element<T> element : getInserted()) {
            if (element.isRealized()) {
                List<T> collected = element.getValues();
                for (int index = 0; index < collected.size(); index++) {
                    if (Objects.equal(collected.get(index), value)) {
                        if (seen) {
                            element.setDuplicate(index);
                        } else {
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
        if (!isAddedBy(provider)) {
            addPendingElement(cachingElement(provider));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        modCount++;
        if (!isAddedBy(provider)) {
            addPendingElement(cachingElement(provider));
            return true;
        } else {
            return false;
        }
    }

    // Compared by provider: an element realized on add keeps its values, so it is not equal to a new one.
    // An element realized by reading the collection doesn't count, so the provider is added and queried again.
    private boolean isAddedBy(ProviderInternal<?> provider) {
        for (Element<T> element : getInserted()) {
            if ((!element.isRealized() || element.isRealizedOnAdd()) && element.isProvidedBy(provider)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean addPendingElement(Element<T> element) {
        boolean added = super.addPendingElement(element);
        if (element.isRealizedOnAdd()) {
            if (valuesRealizedOnAdd.isEmpty()) {
                valuesRealizedOnAdd = new HashSet<>();
            }
            valuesRealizedOnAdd.addAll(element.getValues());
        }
        return added;
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
