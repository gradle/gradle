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
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class IterationOrderRetainingSetElementSource<T> implements ElementSource<T> {
    private final Set<T> values = new LinkedHashSet<T>();
    private final Set<RealizableProvider<T>> orderedValues = new LinkedHashSet<RealizableProvider<T>>();
    private final PendingSource<T> pending = new DefaultPendingSource<T>();

    @Override
    public boolean isEmpty() {
        return values.isEmpty() && pending.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return values.isEmpty() && pending.isEmpty();
    }

    @Override
    public int size() {
        return values.size() + pending.size();
    }

    @Override
    public int estimatedSize() {
        return values.size() + pending.size();
    }

    @Override
    public Iterator<T> iterator() {
        Set<T> allValues = new LinkedHashSet<T>();
        pending.realizePending();
        for (RealizableProvider<? extends T> provider : orderedValues) {
            allValues.add(provider.get());
        }
        return allValues.iterator();
    }

    @Override
    public Iterator<T> iteratorNoFlush() {
        Set<T> allValues = new LinkedHashSet<T>();
        for (RealizableProvider<? extends T> provider : orderedValues) {
            if (provider.isRealized()) {
                allValues.add(provider.get());
            }
        }
        return allValues.iterator();
    }

    @Override
    public boolean contains(Object element) {
        pending.realizePending();
        return values.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        pending.realizePending();
        return values.containsAll(elements);
    }

    @Override
    public boolean add(T element) {
        return values.add(element) && orderedValues.add(new RealizedProvider(element));
    }

    @Override
    public boolean remove(Object o) {
        Iterator<RealizableProvider<T>> iterator = orderedValues.iterator();
        while (iterator.hasNext()) {
            RealizableProvider<? extends T> provider = iterator.next();
            if (provider.isRealized() && provider.get().equals(o)) {
                iterator.remove();
                break;
            }
        }
        return values.remove(o);
    }

    @Override
    public void clear() {
        orderedValues.clear();
        pending.clear();
        values.clear();
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
        pending.addPending(cachingProvider);
        orderedValues.add(cachingProvider);
    }

    @Override
    public void removePending(ProviderInternal<? extends T> provider) {
        Iterator<RealizableProvider<T>> iterator = orderedValues.iterator();
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
    public void onRealize(Action<ProviderInternal<? extends T>> action) {
        pending.onRealize(action);
    }

    private interface RealizableProvider<T> extends ProviderInternal<T> {
        boolean isRealized();
        boolean caches(ProviderInternal<? extends T> provider);
    }

    private class RealizedProvider extends DefaultProvider<T> implements RealizableProvider<T> {
        private final T value;

        RealizedProvider(final T value) {
            super(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return value;
                }
            });
            this.value = value;
        }

        @Override
        public boolean isRealized() {
            return true;
        }

        @Override
        public boolean caches(ProviderInternal<? extends T> provider) {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RealizedProvider that = (RealizedProvider) o;
            return Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    private class CachingProvider extends AbstractProvider<T> implements RealizableProvider<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;

        CachingProvider(final ProviderInternal<? extends T> delegate) {
            this.delegate = delegate;
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
            if (value == null) {
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CachingProvider that = (CachingProvider) o;
            return Objects.equal(delegate, that.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate);
        }
    }
}
