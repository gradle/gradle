/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.cache;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class ManualEvictionInMemoryCache<K, V> implements Cache<K, V> {
    private final ConcurrentMap<K, Entry<V>> map = new ConcurrentHashMap<>(256);

    @Override
    public V get(K key, Function<? super K, ? extends V> factory) {
        while (true) {
            Entry<V> existing = map.get(key);
            if (existing == null) {
                Entry<V> created = new Entry<>(Thread.currentThread());
                existing = map.putIfAbsent(key, created);
                if (existing == null) {
                    return compute(key, factory, created);
                }
            }
            if (existing.owner == Thread.currentThread() && !existing.value.isDone()) {
                throw new IllegalStateException("Recursive update");
            }
            V value = existing.value.join();
            if (value != null) {
                return value;
            }
        }
    }

    private V compute(K key, Function<? super K, ? extends V> factory, Entry<V> created) {
        V value;
        try {
            value = factory.apply(key);
        } catch (Throwable t) {
            map.remove(key, created);
            created.value.complete(null);
            throw t;
        }
        if (value == null) {
            map.remove(key, created);
        }
        created.value.complete(value);
        return value;
    }

    @Override
    public @Nullable V getIfPresent(K key) {
        Entry<V> entry = map.get(key);
        return entry == null ? null : entry.value.getNow(null);
    }

    @Override
    public void put(K key, V value) {
        Entry<V> entry = new Entry<>(null);
        entry.value.complete(value);
        map.put(key, entry);
    }

    public void retainAll(Collection<? extends K> keysToRetain) {
        map.keySet().retainAll(keysToRetain);
    }

    public void clear() {
        map.clear();
    }

    private static final class Entry<V> {
        private final @Nullable Thread owner;
        private final CompletableFuture<@Nullable V> value = new CompletableFuture<>();

        Entry(@Nullable Thread owner) {
            this.owner = owner;
        }
    }
}
