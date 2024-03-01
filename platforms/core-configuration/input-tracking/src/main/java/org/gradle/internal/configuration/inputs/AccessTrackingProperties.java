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

import com.google.common.primitives.Primitives;

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
public class AccessTrackingProperties extends Properties {
    /**
     * A listener that is notified about reads and modifications of the Properties instance.
     * Note that there's no guarantee about the state of the Properties object when the
     * listener's method is called because of modifying operation: it may happen before or after modification.
     */
    public interface Listener {
        /**
         * Called when the property with the name {@code key} is read. The {@code value} is the value of the property observed by the caller.
         * The Properties object may not contain the property with this name, the value is {@code null} then. Note that most modifying methods
         * like {@link Properties#setProperty(String, String)} provide information about the previous value and trigger this method.
         * All modifying operations call this method prior to {@link #onChange(Object, Object)}, {@link #onRemove(Object)} or {@link #onClear()}.
         * <p>
         * When this method is called because of the modifying operation, the state of the observed Properties object is undefined for the duration of the
         * call: it may be already completely or partially modified to reflect the result of the operation.
         *
         * @param key the key used by the caller to access the property
         * @param value the value observed by the caller or {@code null} if there is no value for the given key
         */
        void onAccess(Object key, @Nullable Object value);

        /**
         * Called when the property with the name {@code key} is updated or added. The {@code newValue} is the new value of the property provided by
         * the caller. If the modifying method provides a way for the caller to observe a previous value of the key then
         * {@link #onAccess(Object, Object)} method is called prior to this method.
         * <p>
         * The state of the observed Properties object is undefined for the duration of the call: it may be already completely or partially
         * modified to reflect the result of the operation.
         *
         * @param key the key used by the caller to access the property
         * @param newValue the value provided by the caller
         */
        void onChange(Object key, Object newValue);

        /**
         * Called when the property with the name {@code key} is removed. The Properties object may not contain the property prior to the modification.
         * If the modifying method provides a way for the caller to observe a previous value of the key then {@link #onAccess(Object, Object)} method is
         * called prior to this method.
         * <p>
         * The state of the observed Properties object is undefined for the duration of the call: it may be already completely or partially
         * modified to reflect the result of the operation.
         *
         * @param key the key used by the caller to access the property
         */
        void onRemove(Object key);

        /**
         * Called when the caller unconditionally removes all properties in this Properties object, for example by calling {@link Properties#clear()}.
         * <p>
         * The state of the observed Properties object is undefined for the duration of the call: it may be already completely or partially
         * modified to reflect the result of the operation.
         */
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
        Object key = entry != null ? entry.getKey() : null;
        if (key != null) {
            getAndReportAccess(key);
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
                // It is a bit of optimization to avoid storing unnecessary stores when the value doesn't change.
                // Strings and primitive wrappers are tested with "equals", user types are tested for reference
                // equality to avoid problems with poorly-defined user-provided equality.
                if (!simpleOrRefEquals(newValue, v)) {
                    reportChange(k, newValue);
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
        reportAccess(key, oldValue);
        if (oldValue == null) {
            // Properties disallow null values, so it is safe to assume that the map was changed.
            reportChange(key, value);
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
        reportAccess(key, oldValue);
        if (hadValue) {
            // The configuration cache uses onRemove callback to remember that the property has to be removed.
            // Of course, the property has to be removed in the cached run only if it was removed in the
            // non-cached run first. Changing the value of the property would invalidate the cache and recalculate the removal.
            reportRemoval(key);
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
        reportAccess(key, oldValue);
        if (changed) {
            // The configuration cache uses onChange callback to remember that the property has to be changed.
            // Of course, the property has to be changed in the cached run only if it was changed in the
            // non-cached run first. Changing the value of the property externally would invalidate the cache and recalculate the replacement.
            reportChange(key, newValue);
        }
        return changed;

    }

    @Override
    @Nullable
    public Object replace(Object key, Object value) {
        Object oldValue;
        synchronized (delegate) {
            oldValue = delegate.replace(key, value);
        }
        reportAccess(key, oldValue);
        if (oldValue != null) {
            reportChange(key, value);
        }
        return oldValue;

    }

    @Override
    @Nullable
    public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        Object oldValue;
        Object computedValue = null;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            if (oldValue == null) {
                computedValue = delegate.computeIfAbsent(key, mappingFunction);
            }
        }
        reportAccess(key, oldValue);
        if (computedValue != null) {
            reportChange(key, computedValue);
            return computedValue;
        }
        return oldValue;
    }

    @Override
    @Nullable
    public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object computedValue = null;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            if (oldValue != null) {
                computedValue = delegate.computeIfPresent(key, remappingFunction);
            }
        }
        reportAccess(key, oldValue);
        if (oldValue != null) {
            if (computedValue != null) {
                reportChange(key, computedValue);
            } else {
                reportRemoval(key);
            }
        }
        return computedValue;
    }


    @Override
    @Nullable
    public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object newValue;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            newValue = delegate.compute(key, remappingFunction);
        }
        reportAccess(key, oldValue);
        if (newValue != null) {
            reportChange(key, newValue);
        } else if (oldValue != null) {
            reportRemoval(key);
        }
        return newValue;
    }


    @Override
    @Nullable
    public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        Object oldValue;
        Object newValue;
        synchronized (delegate) {
            oldValue = delegate.get(key);
            newValue = delegate.merge(key, value, remappingFunction);
        }
        reportAccess(key, oldValue);
        if (newValue != null) {
            reportChange(key, newValue);
        } else if (oldValue != null) {
            reportRemoval(key);
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
        return getAndReportAccess(key) != null;
    }

    @Override
    @Nullable
    public Object put(Object key, Object value) {
        Object oldValue;
        synchronized (delegate) {
            oldValue = delegate.put(key, value);
        }
        reportAccess(key, oldValue);
        reportChange(key, value);
        return oldValue;

    }

    @Override
    @Nullable
    public Object setProperty(String key, String value) {
        return put(key, value);
    }

    @Override
    @Nullable
    public Object remove(Object key) {
        Object result;
        synchronized (delegate) {
            result = delegate.remove(key);
        }
        reportAccess(key, result);
        reportRemoval(key);
        return result;

    }

    @Override
    public void putAll(Map<?, ?> t) {
        synchronized (delegate) {
            delegate.putAll(t);
        }
        // putAll has no return value so keys do not become inputs.
        t.forEach(listener::onChange);
    }

    @Override
    public void clear() {
        synchronized (delegate) {
            delegate.clear();
        }
        reportClear();
    }

    @Override
    @Nullable
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
    @Nullable  // The contract here is trickier - the return value is only null if the default value is null, but this method is not really part of any public API.
    public String getProperty(String key, @Nullable String defaultValue) {
        Object oValue = getAndReportAccess(key);
        String value = oValue instanceof String ? (String) oValue : null;
        return value != null ? value : defaultValue;
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        Object value = getAndReportAccess(key);
        return value != null ? value : defaultValue;
    }

    @Override
    @Nullable
    public Object get(Object key) {
        return getAndReportAccess(key);
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

    @Nullable
    private Object getAndReportAccess(@Nullable Object key) {
        Object value = delegate.get(key);
        // delegate.get(null) actually throws NPE.
        assert key != null;
        reportAccess(key, value);
        return value;
    }

    private void reportAccess(Object key, @Nullable Object value) {
        listener.onAccess(key, value);
    }

    private void reportAggregatingAccess() {
        // Mark all map contents as inputs if some aggregating access is used.
        delegate.forEach(this::reportAccess);
    }

    private void reportChange(Object key, Object value) {
        listener.onChange(key, value);
    }

    private void reportRemoval(Object key) {
        listener.onRemove(key);
    }

    private void reportClear() {
        listener.onClear();
    }

    /**
     * Tests equality two objects with {@code equals} if the objects are Strings or primitive wrappers. Otherwise, the equality of references is tested (i.e. {@code lhs == rhs}).
     *
     * @param lhs the first object (can be {@code null})
     * @param rhs the second object (can be {@code null})
     * @return {@code true} if the objects are equal in the sense described above
     */
    private static boolean simpleOrRefEquals(@Nullable Object lhs, @Nullable Object rhs) {
        if (lhs == rhs) {
            return true;
        }
        if (lhs == null || rhs == null) {
            return false;
        }
        Class<?> lhsClass = lhs.getClass();
        if (lhsClass == rhs.getClass() && isSimpleType(lhsClass)) {
            return Objects.equals(lhs, rhs);
        }
        return false;
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class || Primitives.isWrapperType(clazz);
    }

    private AccessTrackingSet.Listener trackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(@Nullable Object o) {
                getAndReportAccess(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(@Nullable Object object) {
                reportRemoval(Objects.requireNonNull(object));
            }

            @Override
            public void onClear() {
                reportClear();
            }
        };
    }

    private AccessTrackingSet.Listener entrySetTrackingListener() {
        return new AccessTrackingSet.Listener() {
            @Override
            public void onAccess(@Nullable Object o) {
                onAccessEntrySetElement(o);
            }

            @Override
            public void onAggregatingAccess() {
                reportAggregatingAccess();
            }

            @Override
            public void onRemove(@Nullable Object potentialEntry) {
                Map.Entry<?, ?> entry = AccessTrackingUtils.tryConvertingToEntry(potentialEntry);
                Object removedKey = entry != null ? entry.getKey() : null;
                if (removedKey != null) {
                    reportRemoval(removedKey);
                }
            }

            @Override
            public void onClear() {
                reportClear();
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
            reportChange(getKey(), value);
            return oldValue;
        }
    }
}
