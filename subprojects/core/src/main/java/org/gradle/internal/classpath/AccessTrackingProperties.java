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

package org.gradle.internal.classpath;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A wrapper for {@link Properties} that notifies a listener about accesses.
 */
class AccessTrackingProperties extends Properties {
    public interface Listener {
        void onAccess(Object key, @Nullable Object value);

        void onChange(Object key, Object newValue);

        void onRemove(Object key);

        void onClear();
    }

    // TODO(https://github.com/gradle/configuration-cache/issues/337) Only a limited subset of method is tracked currently.
    private final Properties delegate;
    private final Listener listener;

    public AccessTrackingProperties(Properties delegate, Listener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public Enumeration<?> propertyNames() {
        reportAggregatingAccess();
        return delegate.propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return new AccessTrackingSet<>(delegate.stringPropertyNames(), trackingListener());
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
    public Enumeration<Object> keys() {
        reportAggregatingAccess();
        return delegate.keys();
    }

    @Override
    public Enumeration<Object> elements() {
        reportAggregatingAccess();
        return delegate.elements();
    }

    @Override
    public Set<Object> keySet() {
        return new AccessTrackingSet<>(delegate.keySet(), trackingListener());
    }

    @Override
    public Collection<Object> values() {
        return delegate.values();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new AccessTrackingSet<>(delegate.entrySet(), entrySetTrackingListener(), TrackingEntry::new);
    }

    private void onAccessEntrySetElement(@Nullable Object potentialEntry) {
        Map.Entry<?, ?> entry = AccessTrackingUtils.tryConvertingToEntry(potentialEntry);
        if (entry != null) {
            getAndReport(entry.getKey());
        }
    }

    @Override
    public void forEach(BiConsumer<? super Object, ? super Object> action) {
        reportAggregatingAccess();
        delegate.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        reportAggregatingAccess();
        synchronized (delegate) {
            delegate.replaceAll((k, v) -> {
                Object newValue = function.apply(k, v);
                if (v != newValue) {
                    // Doing reference comparison may be overzealous for strings,
                    // but it is safer for user types with potentially poorly defined equals.
                    listener.onChange(k, newValue);
                }
                return newValue;
            });
        }
    }

    @Override
    public Object putIfAbsent(Object key, Object value) {
        Object oldValue;
        synchronized (delegate) {
            oldValue = delegate.putIfAbsent(key, value);
        }
        reportKeyAndValue(key, oldValue);
        if (oldValue == null) {
            // Properties disallow null values, so it is safe to assume that the map was changed.
            listener.onChange(key, value);
        }
        return oldValue;

    }

    @Override
    public boolean remove(Object key, Object value) {
        Object oldValue;
        boolean hadValue;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            hadValue = delegate.remove(key, value);
        }
        reportKeyAndValue(key, oldValue);
        if (hadValue) {
            // The configuration cache uses onRemove callback to remember that the property has to be removed.
            // Of course, the property has to be removed in the cached run only if it was removed in the
            // non-cached run first. Changing the value of the property would invalidate the cache and recalculate the removal.
            listener.onRemove(key);
        }
        return hadValue;

    }

    @Override
    public boolean replace(Object key, Object expectedOldValue, Object newValue) {
        Object oldValue;
        boolean changed;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            changed = delegate.replace(key, expectedOldValue, newValue);
        }
        reportKeyAndValue(key, oldValue);
        if (changed) {
            // The configuration cache uses onChange callback to remember that the property has to be changed.
            // Of course, the property has to be changed in the cached run only if it was changed in the
            // non-cached run first. Changing the value of the property externally would invalidate the cache and recalculate the replacement.
            listener.onChange(key, newValue);
        }
        return changed;

    }

    @Override
    public Object replace(Object key, Object value) {
        Object oldValue;
        synchronized (delegate) {
            oldValue = delegate.replace(key, value);
        }
        reportKeyAndValue(key, oldValue);
        if (oldValue != null) {
            listener.onChange(key, value);
        }
        return oldValue;

    }

    @Override
    public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        Object oldValue;
        Object computedValue = null;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            if (oldValue == null) {
                computedValue = delegate.computeIfAbsent(key, mappingFunction);
            }
        }
        reportKeyAndValue(key, oldValue);
        if (computedValue != null) {
            listener.onChange(key, computedValue);
            return computedValue;
        }
        return oldValue;
    }

    @Override
    public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object computedValue = null;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            if (oldValue != null) {
                computedValue = delegate.computeIfPresent(key, remappingFunction);
            }
        }
        reportKeyAndValue(key, oldValue);
        if (oldValue != null) {
            if (computedValue != null) {
                listener.onChange(key, computedValue);
            } else {
                listener.onRemove(key);
            }
        }
        return computedValue;
    }


    @Override
    public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object newValue;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            newValue = delegate.compute(key, remappingFunction);
        }
        reportKeyAndValue(key, oldValue);
        if (newValue != null) {
            listener.onChange(key, newValue);
        } else if (oldValue != null) {
            listener.onRemove(key);
        }
        return newValue;
    }


    @Override
    public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object newValue;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            newValue = delegate.merge(key, value, remappingFunction);
        }
        reportKeyAndValue(key, oldValue);
        if (newValue != null) {
            listener.onChange(key, newValue);
        } else if (oldValue != null) {
            listener.onRemove(key);
        }
        return newValue;

    }

    @Override
    public boolean contains(Object value) {
        return delegate.contains(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        return getAndReport(key) != null;
    }

    @Override
    public Object put(Object key, Object value) {
        Object oldValue;
        synchronized (delegate) {
            oldValue = delegate.put(key, value);
        }
        reportKeyAndValue(key, oldValue);
        listener.onChange(key, value);
        return oldValue;

    }

    @Override
    public Object setProperty(String key, String value) {
        return put(key, value);
    }

    @Override
    public Object remove(Object key) {
        Object result;
        synchronized (delegate) {
            result = delegate.remove(key);
        }
        reportKeyAndValue(key, result);
        listener.onRemove(key);
        return result;

    }

    @Override
    public void putAll(Map<?, ?> t) {
        // putAll has no return value so keys do not become inputs.
        t.forEach(listener::onChange);
        synchronized (delegate) {
            delegate.putAll(t);
        }
    }

    @Override
    public void clear() {
        listener.onClear();
        synchronized (delegate) {
            delegate.clear();
        }
    }

    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        Object oValue = getAndReport(key);
        String value = oValue instanceof String ? (String) oValue : null;
        return value != null ? value : defaultValue;
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        Object value = getAndReport(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Object get(Object key) {
        return getAndReport(key);
    }

    @Override
    public void load(Reader reader) throws IOException {
        delegate.load(reader);
    }

    @Override
    public void load(InputStream inStream) throws IOException {
        delegate.load(inStream);
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void save(OutputStream out, String comments) {
        reportAggregatingAccess();
        delegate.save(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        reportAggregatingAccess();
        delegate.store(writer, comments);
    }

    @Override
    public void store(OutputStream out, @Nullable String comments) throws IOException {
        reportAggregatingAccess();
        delegate.store(out, comments);
    }

    @Override
    public void loadFromXML(InputStream in) throws IOException {
        delegate.loadFromXML(in);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        reportAggregatingAccess();
        delegate.storeToXML(os, comment);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        reportAggregatingAccess();
        delegate.storeToXML(os, comment, encoding);
    }

    @Override
    public void list(PrintStream out) {
        reportAggregatingAccess();
        delegate.list(out);
    }

    @Override
    public void list(PrintWriter out) {
        reportAggregatingAccess();
        delegate.list(out);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object o) {
        reportAggregatingAccess();
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        reportAggregatingAccess();
        return delegate.hashCode();
    }

    private Object getAndReport(Object key) {
        Object value = delegate.get(key);
        reportKeyAndValue(key, value);
        return value;
    }

    private void reportKeyAndValue(Object key, Object value) {
        listener.onAccess(key, value);
    }

    private void reportAggregatingAccess() {
        // Mark all map contents as inputs if some aggregating access is used.
        delegate.forEach(this::reportKeyAndValue);
    }

    private AccessTrackingSet.Listener trackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(Object o) {
                getAndReport(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(Object object) {
                listener.onRemove(object);
            }

            @Override
            public void onClear() {
                listener.onClear();
            }
        };
    }

    private AccessTrackingSet.Listener entrySetTrackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(Object o) {
                onAccessEntrySetElement(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(Object potentialEntry) {
                Map.Entry<?, ?> entry = AccessTrackingUtils.tryConvertingToEntry(potentialEntry);
                if (entry != null) {
                    listener.onRemove(entry.getKey());
                }
            }

            @Override
            public void onClear() {
                listener.onClear();
            }
        };
    }

    private class TrackingEntry implements Map.Entry<Object, Object> {
        private final Map.Entry<Object, Object> delegate;

        TrackingEntry(Map.Entry<Object, Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object getKey() {
            return delegate.getKey();
        }

        @Override
        public Object getValue() {
            return delegate.getValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> that = (Map.Entry<?, ?>) o;
            return Objects.equals(delegate.getKey(), that.getKey()) && Objects.equals(delegate.getValue(), that.getValue());
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public Object setValue(Object value) {
            Object oldValue = delegate.setValue(value);
            listener.onAccess(getKey(), oldValue);
            listener.onChange(getKey(), value);
            return oldValue;
        }
    }
}
