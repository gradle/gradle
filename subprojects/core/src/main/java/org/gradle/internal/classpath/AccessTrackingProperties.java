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
    private final BiConsumer<? super String, Object> onAccess;

    public AccessTrackingProperties(Properties delegate, BiConsumer<? super String, Object> onAccess) {
        this.delegate = delegate;
        this.onAccess = onAccess;
    }

    @Override
    public Enumeration<?> propertyNames() {
        return delegate.propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return delegate.stringPropertyNames();
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
    public Enumeration<Object> keys() {
        return delegate.keys();
    }

    @Override
    public Enumeration<Object> elements() {
        return delegate.elements();
    }

    @Override
    public Set<Object> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<Object> values() {
        return delegate.values();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new AccessTrackingSet<>(
            delegate.entrySet(),
            this::onAccessEntrySetElement);
    }

    private void onAccessEntrySetElement(@Nullable Object potentialEntry) {
        Map.Entry<String, String> entry = AccessTrackingUtils.tryConvertingToTrackableEntry(potentialEntry);
        if (entry != null) {
            onAccess.accept(entry.getKey(), delegate.get(entry.getKey()));
        }
    }

    @Override
    public void forEach(BiConsumer<? super Object, ? super Object> action) {
        delegate.forEach((k, v) -> {
            onAccess.accept((String) k, v);
            action.accept(k, v);
        });
    }

    @Override
    public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
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
        return delegate.containsKey(key);
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
        return delegate.remove(key);
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
        String value = delegate.getProperty(key);
        onAccess.accept(key, value);
        return value != null ? value : defaultValue;
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return getProperty((String) key, (String) defaultValue);
    }

    @Override
    public Object get(Object key) {
        return getProperty((String) key);
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
        delegate.save(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        delegate.store(writer, comments);
    }

    @Override
    public void store(OutputStream out, @Nullable String comments) throws IOException {
        delegate.store(out, comments);
    }

    @Override
    public void loadFromXML(InputStream in) throws IOException {
        delegate.loadFromXML(in);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        delegate.storeToXML(os, comment);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        delegate.storeToXML(os, comment, encoding);
    }

    @Override
    public void list(PrintStream out) {
        delegate.list(out);
    }

    @Override
    public void list(PrintWriter out) {
        delegate.list(out);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
