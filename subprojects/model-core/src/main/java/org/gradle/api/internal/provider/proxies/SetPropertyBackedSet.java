/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.proxies;

import com.google.common.collect.ForwardingSet;
import org.gradle.api.provider.SetProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class SetPropertyBackedSet<E> extends ForwardingSet<E> {

    private final SetProperty<E> delegate;

    public SetPropertyBackedSet(SetProperty<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected @NotNull Set<E> delegate() {
        return new Set<E>() {
            @Override
            public int size() {
                return delegate.get().size();
            }

            @Override
            public boolean isEmpty() {
                return delegate.get().isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                return delegate.get().contains(o);
            }

            @NotNull
            @Override
            public Iterator<E> iterator() {
                // TODO: Should we support Iterator.remove()?
                return delegate.get().iterator();
            }

            @NotNull
            @Override
            public Object[] toArray() {
                return delegate.get().toArray();
            }

            @NotNull
            @Override
            public <T> T[] toArray(@NotNull T[] a) {
                return delegate.get().toArray(a);
            }

            @Override
            public boolean add(E e) {
                Set<E> set = delegate.get();
                boolean added = set.add(e);
                delegate.add(e);
                return added;
            }

            @Override
            public boolean remove(Object o) {
                Set<? extends E> set = delegate.get();
                boolean removed = set.remove(o);
                delegate.set(set);
                return removed;
            }

            @Override
            public boolean containsAll(@NotNull Collection<?> c) {
                return delegate.get().containsAll(c);
            }

            @Override
            public boolean addAll(@NotNull Collection<? extends E> c) {
                Set<E> set = delegate.get();
                boolean added = set.addAll(c);
                delegate.addAll(c);
                return added;
            }

            @Override
            public boolean retainAll(@NotNull Collection<?> c) {
                Set<? extends E> set = delegate.get();
                boolean removed = set.retainAll(c);
                delegate.set(set);
                return removed;
            }

            @Override
            public boolean removeAll(@NotNull Collection<?> c) {
                Set<? extends E> set = delegate.get();
                boolean removed = set.removeAll(c);
                delegate.set(set);
                return removed;
            }

            @Override
            public void clear() {
                delegate.empty();
            }
        };
    }
}
