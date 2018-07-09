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
import org.gradle.api.Action;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class IterationOrderRetainingSetElementSource<T> implements ElementSource<T> {
    // This set represents the elements which have been realized and "added" to the store.  This may
    // or may not be in the order that elements are inserted.  We track this to know which realized
    // elements have already had rules fired against them.
    private final Set<T> added = new HashSet<T>();

    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final Set<SetElement<T>> inserted = new LinkedHashSet<SetElement<T>>();

    // Represents the pending elements that have not yet been realized.
    private final PendingSource<T> pending = new DefaultPendingSource<T>();

    @Override
    public boolean isEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public int size() {
        int count = 0;
        for (SetElement<T> element : inserted) {
            count += element.provider.size();
        }
        return count;
    }

    @Override
    public int estimatedSize() {
        return size();
    }

    @Override
    public Iterator<T> iterator() {
        pending.realizePending();
        Set<T> allValues = new LinkedHashSet<T>();
        for (SetElement<T> element : inserted) {
            allValues.addAll(element.getValues());
        }
        return allValues.iterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return allRealizedValues().iterator();
    }

    private Set<T> allRealizedValues() {
        Set<T> realizedValues = new LinkedHashSet<T>();
        for (SetElement<T> element: inserted) {
            if (element.realized) {
                realizedValues.addAll(element.getValues());
            }
        }
        return realizedValues;
    }

    @Override
    public boolean contains(Object element) {
        pending.realizePending();
        return allRealizedValues().contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        pending.realizePending();
        return allRealizedValues().containsAll(elements);
    }

    @Override
    public boolean add(T element) {
        if (allRealizedValues().contains(element)) {
            // Represents an already inserted value (such as a just-realized provider value)
            // that may or may not have already been "added".
            return added.add(element);
        } else {
            // A realized element that has not been inserted before.
            return added.add(element) && inserted.add(new SetElement<T>(element));
        }
    }

    @Override
    public boolean remove(Object o) {
        Iterator<SetElement<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            SetElement<T> element = iterator.next();
            if (element.realized) {
                Set<T> values = element.getValues();
                if (values.size() == 1 && values.iterator().next().equals(o)) {
                    iterator.remove();
                    return added.remove(o);
                }
            }
        }
        return false;
    }

    @Override
    public void clear() {
        inserted.clear();
        added.clear();
        pending.clear();
    }

    @Override
    public void realizePending() {
        pending.realizePending();
    }

    @Override
    public void realizePending(Class<?> type) {
        pending.realizePending(type);
    }

    @Override
    public void addPending(ProviderInternal<? extends T> provider) {
        pending.addPending(provider);
        inserted.add(new SetElement<T>(provider));
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        Iterator<SetElement<T>> iterator = inserted.iterator();
        while(iterator.hasNext()) {
            SetElement<T> element = iterator.next();
            if (element.equals(new SetElement<T>(provider))) {
                iterator.remove();
            }
        }
        pending.removePending(provider);
    }

    @Override
    public void addPendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        pending.addPendingCollection(provider);
        inserted.add(new SetElement<T>(provider));
    }

    @Override
    public void removePendingCollection(CollectionProviderInternal<T, Set<T>> provider) {
        Iterator<SetElement<T>> iterator = inserted.iterator();
        while(iterator.hasNext()) {
            SetElement<T> element = iterator.next();
            if (element.provider.equals(provider)) {
                iterator.remove();
            }
        }
        pending.removePendingCollection(provider);
    }

    @Override
    public void onRealize(final Action<CollectionProviderInternal<T, Set<T>>> action) {
        pending.onRealize(new Action<CollectionProviderInternal<T, Set<T>>>() {
            @Override
            public void execute(CollectionProviderInternal<T, Set<T>> provider) {
                for (SetElement element : inserted) {
                    if (element.provider.equals(provider)) {
                        element.realized = true;
                    }
                }
                action.execute(provider);
            }
        });
    }

    private static class SetElement<T> {
        private CollectionProviderInternal<T, Set<T>> provider;
        private boolean realized;

        SetElement(CollectionProviderInternal<T, Set<T>> provider) {
            this.provider = provider;
        }

        SetElement(ProviderInternal<? extends T> provider) {
            ProviderInternal<T> providerInternal = Cast.uncheckedCast(provider);
            this.provider = DefaultSetProperty.from(providerInternal);
        }

        SetElement(final T value) {
            this.provider = DefaultSetProperty.from(new DefaultProvider<T>(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return value;
                }
            }));
            this.realized = true;
        }

        public Set<T> getValues() {
            realized = true;
            return provider.get();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SetElement<?> that = (SetElement<?>) o;
            return Objects.equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(provider);
        }
    }
}
