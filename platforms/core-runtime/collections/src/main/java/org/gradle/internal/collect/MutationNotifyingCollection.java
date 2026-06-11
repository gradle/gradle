/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.collect;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link Collection} wrapper that notifies a callback when any mutating method is called,
 * including mutation through {@link #iterator()}.
 *
 * <p>The delegate type parameter {@code C} lets subclasses access the delegate through its
 * specific interface, e.g. {@link MutationNotifyingList} calling {@link java.util.List#get(int)}.
 *
 * <p>Serializes as its delegate: the notification callback is runtime-only wiring, and instances
 * may be captured in task state and serialized to the configuration cache.
 */
public class MutationNotifyingCollection<E, C extends Collection<E>> implements Collection<E>, Serializable {

    final C delegate;
    final transient Consumer<String> onMutation;

    public MutationNotifyingCollection(C delegate, Consumer<String> onMutation) {
        this.delegate = delegate;
        this.onMutation = onMutation;
    }

    // protected so that it is inherited by the Set, List and entry-set subclasses.
    protected Object writeReplace() {
        return delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public Iterator<E> iterator() {
        return new MutationNotifyingIterator<E>(delegate.iterator(), onMutation);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        delegate.forEach(action);
    }

    @Override
    @SuppressWarnings("UndefinedEquals") // We're fine with having weak contract of Iterable/Collection.equals.
    public boolean equals(@Nullable Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean add(E e) {
        onMutation.accept("add(Object)");
        return delegate.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        onMutation.accept("addAll(Collection)");
        return delegate.addAll(c);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        onMutation.accept("remove(Object)");
        return delegate.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        onMutation.accept("removeAll(Collection)");
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        onMutation.accept("retainAll(Collection)");
        return delegate.retainAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        onMutation.accept("removeIf(Predicate)");
        return delegate.removeIf(filter);
    }

    @Override
    public void clear() {
        onMutation.accept("clear()");
        delegate.clear();
    }
}
