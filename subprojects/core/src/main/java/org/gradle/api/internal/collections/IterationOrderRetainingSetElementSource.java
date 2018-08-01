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
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;

import java.util.Iterator;

public class IterationOrderRetainingSetElementSource<T> extends AbstractIterationOrderRetainingElementSource<T> {
    private final Spec<Element<T>> noDuplicates = new Spec<Element<T>>() {
        @Override
        public boolean isSatisfiedBy(Element<T> element) {
            return !element.isDuplicate();
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
            getInserted().add(new CachingElement<T>(element));
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

    private void markDuplicates(T value) {
        boolean seen = false;
        for (Element<T> element : getInserted()) {
            if (element.isRealized() && Objects.equal(element.getValue(), value)) {
                if (seen) {
                    element.setDuplicate(true);
                } else {
                    seen = true;
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

}
