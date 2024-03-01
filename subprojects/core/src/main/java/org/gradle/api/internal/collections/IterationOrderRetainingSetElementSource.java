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
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;

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
    private final Set<T> nonProvidedValues = new HashSet<>();

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new RealizedElementCollectionIterator(getInserted(), NO_DUPLICATES);
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new RealizedElementCollectionIterator(getInserted(), NO_DUPLICATES);
    }

    @Override
    public boolean add(T element) {
        modCount++;
        if (nonProvidedValues.add(element)) {
            getInserted().add(new Element<T>(element));
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
        boolean wasRealized = element.isRealized();
        super.clearCachedElement(element);
        if (wasRealized) {
            for (T value : element.getValues()) {
                markDuplicates(value);
            }
        }
    }

    @Override
    public boolean remove(Object o) {
        nonProvidedValues.remove(o);
        return super.remove(o);
    }

    @Override
    public void clear() {
        nonProvidedValues.clear();
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
        Element<T> element = cachingElement(provider);
        if (!getInserted().contains(element)) {
            addPendingElement(element);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        modCount++;
        Element<T> element = cachingElement(provider);
        if (!getInserted().contains(element)) {
            addPendingElement(element);
            return true;
        } else {
            return false;
        }
    }
}
