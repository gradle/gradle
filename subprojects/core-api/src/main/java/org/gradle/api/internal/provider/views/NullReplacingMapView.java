/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.Collections2;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An adapter for a {@code Map<K, Object>} instance that doesn't permit {@code null} values.
 *
 * @param <K> the type of key
 */
public class NullReplacingMapView<K> extends ForwardingMap<K, Object> {
    private static class NullSentinel {
        private static final NullSentinel INSTANCE = new NullSentinel();
    }

    private static Object toDelegate(@Nullable Object value) {
        return value == null ? NullSentinel.INSTANCE : value;
    }

    private static @Nullable Object fromDelegate(@Nullable Object value) {
        return value == NullSentinel.INSTANCE ? null : value;
    }

    private final Map<K, Object> delegate;

    public NullReplacingMapView(Map<K, Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected Map<K, Object> delegate() {
        return delegate;
    }

    @Override
    @Nullable
    public Object get(@Nullable Object key) {
        return fromDelegate(delegate.get(key));
    }

    @Override
    @Nullable
    public Object getOrDefault(Object key, Object defaultValue) {
        Object delegateValue = delegate.get(key);
        if (delegateValue == null) {
            return defaultValue;
        }
        return fromDelegate(delegateValue);
    }

    @Nullable
    @Override
    public Object remove(@Nullable Object key) {
        return fromDelegate(delegate.remove(toDelegate(key)));
    }

    @Override
    public boolean containsValue(@Nullable Object value) {
        return delegate.containsValue(toDelegate(value));
    }

    @Override
    public Collection<Object> values() {
        return Collections2.transform(delegate.values(), NullReplacingMapView::fromDelegate);
    }

    @Override
    @Nullable
    public Object put(K key, @Nullable Object value) {
        return fromDelegate(delegate.put(key, toDelegate(value)));
    }

    @Override
    public void putAll(Map<? extends K, ?> map) {
        delegate.putAll(Maps.transformValues(map, NullReplacingMapView::toDelegate));
    }

    @Override
    public Set<Map.Entry<K, Object>> entrySet() {
        return new EntrySet();
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, Object>> {
        @Override
        public Iterator<Map.Entry<K, Object>> iterator() {
            return Iterators.transform(delegate.entrySet().iterator(), Entry::new);
        }

        @Override
        public int size() {
            return NullReplacingMapView.this.size();
        }
    }

    private static class Entry<K> implements Map.Entry<K, Object> {
        private final Map.Entry<K, Object> delegateEntry;

        private Entry(Map.Entry<K, Object> delegateEntry) {
            this.delegateEntry = delegateEntry;
        }

        @Override
        public K getKey() {
            return delegateEntry.getKey();
        }

        @Override
        @Nullable
        public Object getValue() {
            return fromDelegate(delegateEntry.getValue());
        }

        @Override
        @Nullable
        public Object setValue(@Nullable Object value) {
            return fromDelegate(delegateEntry.setValue(toDelegate(value)));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof Map.Entry<?, ?>) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                return Objects.equals(getKey(), entry.getKey()) && Objects.equals(getValue(), entry.getValue());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return delegateEntry.hashCode();
        }
    }
}
