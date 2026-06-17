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
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A generic decorator for a {@link Collection} that reports every mutating operation to an
 * {@link Interceptor}, including mutations reached through {@link #iterator()}. Reads pass straight
 * through. It owns the tricky mechanics (forwarding, wrapping the iterator, applying an element view,
 * serializing as the delegate) so callers only supply the notification.
 *
 * @param <E> the element type
 * @param <C> the delegate's concrete collection type, so subtypes can reach its specific methods
 */
public class InterceptingCollection<E, C extends Collection<E>> implements Collection<E>, Serializable {

    /**
     * Notified whenever a mutating method is called, with the source-level {@code methodSignature} of
     * that method (e.g. {@code "add(Object)"}). A view prepends its path, e.g. {@code "keySet().clear()"}.
     */
    public interface Interceptor {
        void onMutate(String methodSignature);
    }

    protected final C delegate;
    final transient Interceptor interceptor;
    private final transient Function<E, E> elementView;
    private final transient boolean wrapsElements;

    public InterceptingCollection(C delegate, Interceptor interceptor) {
        this.delegate = delegate;
        this.interceptor = interceptor;
        this.elementView = Function.identity();
        this.wrapsElements = false;
    }

    protected InterceptingCollection(C delegate, Interceptor interceptor, Function<E, E> elementView) {
        this.delegate = delegate;
        this.interceptor = interceptor;
        this.elementView = elementView;
        this.wrapsElements = true;
    }

    private E view(E element) {
        return elementView.apply(element);
    }

    // These decorators serialize as their plain delegate: the interceptor is transient runtime wiring,
    // so a decorated collection captured in serializable state round-trips as the underlying collection.
    protected Object writeReplace() {
        return delegate;
    }

    // region reads
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
        Iterator<E> it = delegate.iterator();
        return new Iterator<E>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                return view(it.next());
            }

            @Override
            public void remove() {
                interceptor.onMutate("iterator().remove()");
                it.remove();
            }
        };
    }

    @Override
    public Object[] toArray() {
        return viewAll(delegate.toArray());
    }

    @Override
    @SuppressWarnings("SuspiciousToArrayCall")
    public <T> T[] toArray(T[] a) {
        return viewAll(delegate.toArray(a));
    }

    @SuppressWarnings("unchecked")
    private <T> T[] viewAll(T[] array) {
        if (wrapsElements) {
            for (int i = 0; i < array.length; i++) {
                array[i] = (T) view((E) array[i]);
            }
        }
        return array;
    }

    @Override
    public Spliterator<E> spliterator() {
        if (!wrapsElements) {
            return delegate.spliterator();
        }
        // Elements are wrapped (e.g. entries whose setValue is intercepted), so stream/spliterator must
        // hand out wrapped elements too. Route through the wrapping iterator at the cost of split quality.
        return Spliterators.spliterator(iterator(), delegate.size(), Spliterator.DISTINCT);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        delegate.forEach(e -> action.accept(view(e)));
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
    // endregion

    // region mutations
    @Override
    public boolean add(E e) {
        interceptor.onMutate("add(Object)");
        return delegate.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        interceptor.onMutate("addAll(Collection)");
        return delegate.addAll(c);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        interceptor.onMutate("remove(Object)");
        return delegate.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        interceptor.onMutate("removeAll(Collection)");
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        interceptor.onMutate("retainAll(Collection)");
        return delegate.retainAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        interceptor.onMutate("removeIf(Predicate)");
        return delegate.removeIf(filter);
    }

    @Override
    public void clear() {
        interceptor.onMutate("clear()");
        delegate.clear();
    }
    // endregion
}
