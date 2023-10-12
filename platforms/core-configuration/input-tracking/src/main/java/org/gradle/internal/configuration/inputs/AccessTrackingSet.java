/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.configuration.inputs;

import com.google.common.collect.ForwardingSet;
import com.google.common.collect.Iterators;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

/**
 * The special-cased implementation of {@link Set} that tracks all accesses to its elements.
 *
 * @param <E> the type of elements
 */
class AccessTrackingSet<E> extends ForwardingSet<E> {
    public interface Listener {
        void onAccess(Object o);

        void onAggregatingAccess();

        void onRemove(Object object);

        void onClear();
    }

    // TODO(https://github.com/gradle/configuration-cache/issues/337) Only a limited subset of entrySet/keySet methods are currently tracked.
    private final Set<? extends E> delegate;
    private final Listener listener;
    private final Function<E, E> factory;

    public AccessTrackingSet(Set<? extends E> delegate, Listener listener) {
        this(delegate, listener, Function.identity());
    }

    public AccessTrackingSet(Set<? extends E> delegate, Listener listener, Function<E, E> factory) {
        this.delegate = delegate;
        this.listener = listener;
        this.factory = factory;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        boolean result = delegate.contains(o);
        listener.onAccess(o);
        return result;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> collection) {
        boolean result = delegate.containsAll(collection);
        for (Object o : collection) {
            listener.onAccess(o);
        }
        return result;
    }

    @Override
    public boolean remove(Object o) {
        // We cannot perform modification before notifying because the listener may want to query the state of the delegate prior to that.
        listener.onAccess(o);
        listener.onRemove(o);
        return delegate.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        // We cannot perform modification before notifying because the listener may want to query the state of the delegate prior to that.
        for (Object o : collection) {
            listener.onAccess(o);
            listener.onRemove(o);
        }
        return delegate.removeAll(collection);
    }

    @Override
    public void clear() {
        delegate.clear();
        listener.onClear();
    }

    @Override
    public Iterator<E> iterator() {
        reportAggregatingAccess();
        return Iterators.transform(delegate().iterator(), factory::apply);
    }

    @Override
    public int size() {
        reportAggregatingAccess();
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        reportAggregatingAccess();
        return delegate.isEmpty();
    }

    @Override
    public boolean equals(@Nullable Object object) {
        reportAggregatingAccess();
        return super.equals(object);
    }

    @Override
    public int hashCode() {
        reportAggregatingAccess();
        return super.hashCode();
    }

    @Override
    public Object[] toArray() {
        // this is basically a reimplementation of the standardToArray that doesn't call this.size()
        // and avoids double-reporting of the aggregating access.
        return toArray(new Object[0]);
    }

    @Override
    @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
    public <T> T[] toArray(T[] array) {
        reportAggregatingAccess();
        T[] result = delegate().toArray(array);
        for (int i = 0; i < result.length; ++i) {
            // The elements of result have to be of some subtype of E because of Set's invariant,
            // so the inner cast is safe. The outer cast might be problematic if T is a some subtype
            // of E and the factory function returns some other subtype. However, this is unlikely
            // to happen in our use cases. Only System.getProperties().entrySet implementation uses
            // this conversion.
            result[i] = (T) factory.apply((E) result[i]);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Set<E> delegate() {
        // The entrySet/keySet disallow adding elements to the set, making it covariant, so downcast is safe there.
        return (Set<E>) delegate;
    }

    private void reportAggregatingAccess() {
        listener.onAggregatingAccess();
    }
}
