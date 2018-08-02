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
import com.google.common.collect.Iterators;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;

import java.util.Iterator;
import java.util.List;

public class IterationOrderRetainingSetElementSource<T> extends AbstractIterationOrderRetainingElementSource<T> {
    private final Spec<ValuePointer<T>> noDuplicates = new Spec<ValuePointer<T>>() {
        @Override
        public boolean isSatisfiedBy(ValuePointer<T> pointer) {
            return !pointer.getElement().isDuplicate(pointer.getIndex());
        }
    };

    @Override
    public Iterator<T> iterator() {
        realizePending();
        return new RealizedElementCollectionIterator<T>(getInserted(), noDuplicates);
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return new RealizedElementCollectionIterator<T>(getInserted(), noDuplicates);
    }

    @Override
    public boolean add(T element) {
        if (!Iterators.contains(iteratorNoFlush(), element)) {
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
        Element<T> element = cachingElement(provider);
        if (!getInserted().contains(element)) {
            getInserted().add(element);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
        Element<T> element = cachingElement(provider);
        if (!getInserted().contains(element)) {
            getInserted().add(element);
            return true;
        } else {
            return false;
        }
    }
}
