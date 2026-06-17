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
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gradle.internal.collect.InterceptingCollection.Interceptor;

/**
 * A generic {@link Map} decorator that reports every mutating operation to an {@link Interceptor},
 * including mutations reached through the {@code keySet()}, {@code values()} and {@code entrySet()}
 * views and through {@link java.util.Map.Entry#setValue}. Reads pass straight through. Companion to
 * {@link InterceptingCollection}.
 */
public class InterceptingMap<K, V> implements Map<K, V>, Serializable {

    final Map<K, V> delegate;
    private final transient Interceptor interceptor;

    public InterceptingMap(Map<K, V> delegate, Interceptor interceptor) {
        this.delegate = delegate;
        this.interceptor = interceptor;
    }

    // These decorators serialize as their plain delegate: the interceptor is transient runtime wiring,
    // so a decorated map captured in serializable state round-trips as the underlying map.
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
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public @Nullable V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach(action);
    }

    @Override
    @SuppressWarnings("UndefinedEquals")
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

    // region views
    @Override
    public Set<K> keySet() {
        // Map views are live: removing from them removes the corresponding mappings from this map.
        return new InterceptingSet<>(delegate.keySet(), sig -> interceptor.onMutate("keySet()." + sig));
    }

    @Override
    public Collection<V> values() {
        return new InterceptingCollection<>(delegate.values(), sig -> interceptor.onMutate("values()." + sig));
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Interceptor entrySetInterceptor = sig -> interceptor.onMutate("entrySet()." + sig);
        Function<Entry<K, V>, Entry<K, V>> wrapEntry = entry -> interceptingEntry(entry, entrySetInterceptor);
        return new InterceptingSet<>(delegate.entrySet(), entrySetInterceptor, wrapEntry);
    }

    private static <K, V> Entry<K, V> interceptingEntry(Entry<K, V> entry, Interceptor interceptor) {
        return new Entry<K, V>() {
            @Override
            public K getKey() {
                return entry.getKey();
            }

            @Override
            public V getValue() {
                return entry.getValue();
            }

            @Override
            public V setValue(V value) {
                interceptor.onMutate("Entry.setValue(Object)");
                return entry.setValue(value);
            }

            @Override
            public boolean equals(Object o) {
                return entry.equals(o);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }

            @Override
            public String toString() {
                return entry.toString();
            }
        };
    }
    // endregion

    // region mutations
    @Override
    public @Nullable V put(K key, V value) {
        interceptor.onMutate("put(Object, Object)");
        return delegate.put(key, value);
    }

    @Override
    public @Nullable V putIfAbsent(K key, V value) {
        interceptor.onMutate("putIfAbsent(Object, Object)");
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        interceptor.onMutate("putAll(Map)");
        delegate.putAll(m);
    }

    @Override
    public V remove(@Nullable Object key) {
        interceptor.onMutate("remove(Object)");
        return delegate.remove(key);
    }

    @Override
    public boolean remove(Object key, Object value) {
        interceptor.onMutate("remove(Object, Object)");
        return delegate.remove(key, value);
    }

    @Override
    public @Nullable V replace(K key, V value) {
        interceptor.onMutate("replace(Object, Object)");
        return delegate.replace(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        interceptor.onMutate("replace(Object, Object, Object)");
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        interceptor.onMutate("replaceAll(BiFunction)");
        delegate.replaceAll(function);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        interceptor.onMutate("merge(Object, Object, BiFunction)");
        return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public @Nullable V compute(K key, BiFunction<? super K, ? super @Nullable V, ? extends V> remappingFunction) {
        interceptor.onMutate("compute(Object, BiFunction)");
        return delegate.compute(key, remappingFunction);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        interceptor.onMutate("computeIfAbsent(Object, Function)");
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public @Nullable V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        interceptor.onMutate("computeIfPresent(Object, BiFunction)");
        return delegate.computeIfPresent(key, remappingFunction);
    }

    @Override
    public void clear() {
        interceptor.onMutate("clear()");
        delegate.clear();
    }
    // endregion
}
