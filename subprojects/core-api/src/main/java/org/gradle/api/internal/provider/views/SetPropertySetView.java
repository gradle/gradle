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

package org.gradle.api.internal.provider.views;

import org.gradle.api.provider.SetProperty;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implementation of Set, that is used for Property upgrades
 */
@NotThreadSafe
public class SetPropertySetView<E> extends AbstractSet<E> {

    private final SetProperty<E> delegate;

    public SetPropertySetView(SetProperty<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean add(E e) {
        boolean added = !contains(e);
        delegate.add(e);
        return added;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean added = false;
        for (E e : c) {
            added |= add(e);
        }
        return added;
    }

    @Override
    public boolean contains(Object o) {
        return delegate.get().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.get().containsAll(c);
    }

    @Override
    public boolean remove(Object o) {
        Set<? extends E> set = new LinkedHashSet<>(delegate.get());
        boolean removed = set.remove(o);
        delegate.set(set);
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Set<? extends E> set = new LinkedHashSet<>(delegate.get());
        boolean removed = set.removeAll(c);
        delegate.set(set);
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Set<? extends E> set = new LinkedHashSet<>(delegate.get());
        boolean removed = set.retainAll(c);
        delegate.set(set);
        return removed;
    }

    @Override
    public void clear() {
        delegate.empty();
    }

    @Override
    public Iterator<E> iterator() {
        Set<E> set = new LinkedHashSet<>(delegate.get());
        Iterator<E> it = set.iterator();
        return new Iterator<E>() {
            E previousValue = null;
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                previousValue = it.next();
                return previousValue;
            }

            @Override
            public void remove() {
                it.remove();
                SetPropertySetView.this.remove(previousValue);
            }
        };
    }

    @Override
    public int size() {
        return delegate.get().size();
    }
}
