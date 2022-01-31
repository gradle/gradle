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
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A wrapper for {@link Properties} that notifies a listener about accesses.
 * interface.
 */
class AccessTrackingProperties extends Properties {
    // TODO(https://github.com/gradle/configuration-cache/issues/337) Only a limited subset of method is tracked currently.
    private final Properties delegate;
    private final BiConsumer<? super String, ? super String> onAccess;

    public AccessTrackingProperties(Properties delegate, BiConsumer<? super String, ? super String> onAccess) {
        this.delegate = delegate;
        this.onAccess = onAccess;
    }

    @Override
    public Enumeration<?> propertyNames() {
        reportAggregatingAccess();
        return delegate.propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return new AccessTrackingSet<>(delegate.stringPropertyNames(), this::getAndReport, this::reportAggregatingAccess);
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
        return new AccessTrackingSet<>(delegate.keySet(), this::getAndReport, this::reportAggregatingAccess);
    }

    @Override
    public Collection<Object> values() {
        return delegate.values();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new AccessTrackingSet<>(delegate.entrySet(), this::onAccessEntrySetElement, this::reportAggregatingAccess);
    }

    private void onAccessEntrySetElement(@Nullable Object potentialEntry) {
        Map.Entry<String, String> entry = AccessTrackingUtils.tryConvertingToTrackableEntry(potentialEntry);
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
        delegate.replaceAll(function);
    }

    @Override
    public Object putIfAbsent(Object key, Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return delegate.remove(key, value);
    }

    @Override
    public boolean replace(Object key, Object oldValue, Object newValue) {
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public Object replace(Object key, Object value) {
        return delegate.replace(key, value);
    }

    @Override
    public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.computeIfPresent(key, remappingFunction);
    }

    @Override
    public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.compute(key, remappingFunction);
    }

    @Override
    public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return delegate.merge(key, value, remappingFunction);
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
        return delegate.put(key, value);
    }

    @Override
    public Object setProperty(String key, String value) {
        return delegate.setProperty(key, value);
    }

    @Override
    public Object remove(Object key) {
        Object result = delegate.remove(key);
        reportKeyAndValue(key, result);
        return result;
    }

    @Override
    public void putAll(Map<?, ?> t) {
        delegate.putAll(t);
    }

    @Override
    public void clear() {
        delegate.clear();
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
        if (key instanceof String && (value == null || value instanceof String)) {
            onAccess.accept((String) key, (String) value);
        }
    }

    private void reportAggregatingAccess() {
        // Mark all map contents as inputs if some aggregating access is used.
        delegate.forEach(this::reportKeyAndValue);
    }
}
