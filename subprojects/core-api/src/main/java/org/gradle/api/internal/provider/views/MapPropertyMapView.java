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

import org.gradle.api.provider.MapProperty;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of Map, that is used for Property upgrades
 */
@NotThreadSafe
public class MapPropertyMapView<K, V> extends AbstractMap<K, V> {

    private final MapProperty<K, V> delegate;

    public MapPropertyMapView(MapProperty<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nullable
    public V get(Object key) {
        return delegate.get().get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.get().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.get().containsValue(value);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    @Nullable
    public V put(K key, V value) {
        V oldValue = get(key);
        delegate.put(key, value);
        return oldValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
    }

    @Override
    @Nullable
    public V remove(Object key) {
        Map<K, V> map = new LinkedHashMap<>(delegate.get());
        V oldValue = map.remove(key);
        delegate.set(map);
        return oldValue;
    }

    @Override
    public void clear() {
        delegate.empty();
    }

    @Override
    public int size() {
        return delegate.get().size();
    }

    private class EntrySet extends AbstractSet<Entry<K, V>> {

        @Override
        public boolean remove(Object o) {
            if (o instanceof Entry && MapPropertyMapView.this.containsKey(((Entry<?, ?>) o).getKey())) {
                Entry<?, ?> entry = (Entry<?, ?>) o;
                V value = MapPropertyMapView.this.get(entry.getKey());
                if (Objects.equals(value, entry.getValue())) {
                    MapPropertyMapView.this.remove(entry.getKey());
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            Iterator<Entry<K, V>> it = new LinkedHashMap<>(MapPropertyMapView.this.delegate.get()).entrySet().iterator();
            return new Iterator<Entry<K, V>>() {
                Entry<K, V> previousValue = null;
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    previousValue = it.next();
                    return previousValue;
                }

                @Override
                public void remove() {
                    it.remove();
                    MapPropertyMapView.this.remove(previousValue.getKey());
                }
            };
        }

        @Override
        public int size() {
            return MapPropertyMapView.this.size();
        }
    }
}
