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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link Map} wrapper that notifies a callback when any mutating method is called,
 * including mutation through the {@link #keySet()}, {@link #values()} and {@link #entrySet()} views.
 *
 * <p>Serializes as its delegate: the notification callback is runtime-only wiring, and instances
 * may be captured in task state and serialized to the configuration cache.
 */
public class MutationNotifyingMap<K, V> implements Map<K, V>, Serializable {

    private final Map<K, V> delegate;
    private final transient Consumer<String> onMutation;

    public MutationNotifyingMap(Map<K, V> delegate, Consumer<String> onMutation) {
        this.delegate = delegate;
        this.onMutation = onMutation;
    }

    private Object writeReplace() {
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
    public Set<K> keySet() {
        // Map views are live: removing from them removes the corresponding mappings from this map.
        return new MutationNotifyingSet<>(delegate.keySet(), name -> onMutation.accept("keySet()." + name));
    }

    @Override
    public Collection<V> values() {
        return new MutationNotifyingCollection<>(delegate.values(), name -> onMutation.accept("values()." + name));
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new MutationNotifyingEntrySet<>(delegate.entrySet(), name -> onMutation.accept("entrySet()." + name));
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach(action);
    }

    @Override
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
    public @Nullable V put(K key, V value) {
        onMutation.accept("put(Object, Object)");
        return delegate.put(key, value);
    }

    @Override
    public @Nullable V putIfAbsent(K key, V value) {
        onMutation.accept("putIfAbsent(Object, Object)");
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        onMutation.accept("putAll(Map)");
        delegate.putAll(m);
    }

    @Override
    public V remove(@Nullable Object key) {
        onMutation.accept("remove(Object)");
        return delegate.remove(key);
    }

    @Override
    public boolean remove(Object key, Object value) {
        onMutation.accept("remove(Object, Object)");
        return delegate.remove(key, value);
    }

    @Override
    public @Nullable V replace(K key, V value) {
        onMutation.accept("replace(Object, Object)");
        return delegate.replace(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        onMutation.accept("replace(Object, Object, Object)");
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        onMutation.accept("replaceAll(BiFunction)");
        delegate.replaceAll(function);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        onMutation.accept("merge(Object, Object, BiFunction)");
        return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public @Nullable V compute(K key, BiFunction<? super K, ? super @Nullable V, ? extends V> remappingFunction) {
        onMutation.accept("compute(Object, BiFunction)");
        return delegate.compute(key, remappingFunction);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        onMutation.accept("computeIfAbsent(Object, Function)");
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public @Nullable V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        onMutation.accept("computeIfPresent(Object, BiFunction)");
        return delegate.computeIfPresent(key, remappingFunction);
    }

    @Override
    public void clear() {
        onMutation.accept("clear()");
        delegate.clear();
    }
}
