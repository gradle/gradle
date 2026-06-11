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

import java.util.Map;
import java.util.function.Consumer;

/**
 * A {@link Map.Entry} wrapper that notifies a callback when {@link #setValue(Object)} is called.
 *
 * <p>Entries obtained from a map's entry set are live: {@code setValue} writes through to the
 * backing map, so it must be guarded like any other mutator.
 */
class MutationNotifyingEntry<K, V> implements Map.Entry<K, V> {

    private final Map.Entry<K, V> delegate;
    private final Consumer<String> onMutation;

    MutationNotifyingEntry(Map.Entry<K, V> delegate, Consumer<String> onMutation) {
        this.delegate = delegate;
        this.onMutation = onMutation;
    }

    @Override
    public K getKey() {
        return delegate.getKey();
    }

    @Override
    public V getValue() {
        return delegate.getValue();
    }

    @Override
    public V setValue(V value) {
        onMutation.accept("Entry.setValue(Object)");
        return delegate.setValue(value);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        // Map.Entry equality is defined value-wise across implementations, so delegating is symmetric.
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
}
