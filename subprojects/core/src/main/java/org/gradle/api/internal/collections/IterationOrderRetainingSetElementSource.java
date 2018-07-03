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
import org.gradle.api.internal.provider.ProviderInternal;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class IterationOrderRetainingSetElementSource<T> implements ElementSource<T> {
    // This set represents the elements which have been realized and "added" to the store.  This may
    // or may not be in the order that elements are inserted.  We track this to know which realized
    // elements have already had rules fired against them.
    private final Set<T> added = new HashSet<T>();

    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final Set<RealizableProvider<T>> inserted = new LinkedHashSet<RealizableProvider<T>>();

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
        return inserted.size();
    }

    @Override
    public int estimatedSize() {
        return inserted.size();
    }

    @Override
    public Iterator<T> iterator() {
        pending.realizePending();
        Set<T> allValues = new LinkedHashSet<T>();
        for (RealizableProvider<? extends T> provider : inserted) {
            allValues.add(provider.get());
        }
        return allValues.iterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        return allRealizedValues().iterator();
    }

    private Set<T> allRealizedValues() {
        Set<T> realizedValues = new LinkedHashSet<T>();
        for (RealizableProvider<? extends T> provider : inserted) {
            if (provider.isRealized()) {
                realizedValues.add(provider.get());
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
            return added.add(element) && inserted.add(new CachingProvider(element));
        }
    }

    @Override
    public boolean remove(Object o) {
        Iterator<RealizableProvider<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            RealizableProvider<? extends T> provider = iterator.next();
            if (provider.isRealized() && provider.get().equals(o)) {
                iterator.remove();
                return added.remove(o);
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
        CachingProvider cachingProvider = new CachingProvider(provider);
        pending.addPending(provider);
        inserted.add(cachingProvider);
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        Iterator<RealizableProvider<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            RealizableProvider<T> next = iterator.next();
            if (next.caches(provider)) {
                iterator.remove();
                break;
            }
        }
        pending.removePending(provider);
    }

    @Override
    public void onRealize(final Action<ProviderInternal<? extends T>> action) {
        Action<ProviderInternal<? extends T>> useCachedProviderAction = new Action<ProviderInternal<? extends T>>() {
            @Override
            public void execute(ProviderInternal<? extends T> realizedProvider) {
                // Use the caching provider on realization so that we only call get() once and then cache it
                for (RealizableProvider<T> provider : inserted) {
                    if (provider.caches(realizedProvider)) {
                        action.execute(provider);
                        return;
                    }
                }
            }
        };
        pending.onRealize(useCachedProviderAction);
    }

    private interface RealizableProvider<T> extends ProviderInternal<T> {
        boolean isRealized();
        boolean caches(ProviderInternal<? extends T> provider);
    }

    private class CachingProvider extends AbstractProvider<T> implements RealizableProvider<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;

        CachingProvider(final ProviderInternal<? extends T> delegate) {
            this.delegate = delegate;
        }

        CachingProvider(T value) {
            this.value = value;
            this.realized = true;
            this.delegate = null;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Override
        public boolean isRealized() {
            return realized;
        }

        @Nullable
        @Override
        public T getOrNull() {
            if (value == null && delegate != null) {
                value = delegate.get();
                realized = true;
            }
            return value;
        }

        @Override
        public boolean caches(ProviderInternal<? extends T> provider) {
            return Objects.equal(delegate, provider);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachingProvider that = (CachingProvider) o;
            return Objects.equal(delegate, that.delegate) &&
                Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate, value);
        }
    }
}
