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

package org.gradle.internal.classpath;

import com.google.common.collect.AbstractIterator;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.codehaus.groovy.runtime.wrappers.Wrapper;

import javax.annotation.Nullable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Instrumented {
    private static final Listener NO_OP = (key, value, consumer) -> {
    };
    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(NO_OP);

    public static void setListener(Listener listener) {
        LISTENER.set(listener);
    }

    public static void discardListener() {
        LISTENER.set(NO_OP);
    }

    // Called by generated code
    @SuppressWarnings("unused")
    public static void groovyCallSites(CallSiteArray array) {
        for (CallSite callSite : array.array) {
            switch (callSite.getName()) {
                case "getProperty":
                    array.array[callSite.getIndex()] = new SystemPropertyCallSite(callSite);
                    break;
                case "properties":
                    array.array[callSite.getIndex()] = new SystemPropertiesCallSite(callSite);
                    break;
                case "getInteger":
                    array.array[callSite.getIndex()] = new IntegerSystemPropertyCallSite(callSite);
                    break;
                case "getLong":
                    array.array[callSite.getIndex()] = new LongSystemPropertyCallSite(callSite);
                    break;
                case "getBoolean":
                    array.array[callSite.getIndex()] = new BooleanSystemPropertyCallSite(callSite);
                    break;
            }
        }
    }

    // Called by generated code.
    public static String systemProperty(String key, String consumer) {
        return systemProperty(key, null, consumer);
    }

    // Called by generated code.
    public static String systemProperty(String key, @Nullable String defaultValue, String consumer) {
        String value = System.getProperty(key);
        LISTENER.get().systemPropertyQueried(key, value, consumer);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    // Called by generated code.
    public static Properties systemProperties(String consumer) {
        return new DecoratingProperties(System.getProperties(), consumer);
    }

    // Called by generated code.
    public static Integer getInteger(String key, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Integer.getInteger(key);
    }

    // Called by generated code.
    public static Integer getInteger(String key, int defaultValue, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Integer getInteger(String key, Integer defaultValue, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Long.getLong(key);
    }

    // Called by generated code.
    public static Long getLong(String key, long defaultValue, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, Long defaultValue, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static boolean getBoolean(String key, String consumer) {
        LISTENER.get().systemPropertyQueried(key, System.getProperty(key), consumer);
        return Boolean.getBoolean(key);
    }

    private static Object unwrap(Object obj) {
        if (obj instanceof Wrapper) {
            return ((Wrapper) obj).unwrap();
        }
        return obj;
    }

    private static String convertToString(Object arg) {
        if (arg instanceof CharSequence) {
            return ((CharSequence) arg).toString();
        }
        return (String) arg;
    }

    public interface Listener {
        /**
         * @param consumer The name of the class that is reading the property value
         */
        void systemPropertyQueried(String key, @Nullable Object value, String consumer);
    }

    private static class DecoratingProperties extends Properties {
        private final String consumer;
        private final Properties delegate;

        public DecoratingProperties(Properties delegate, String consumer) {
            this.consumer = consumer;
            this.delegate = delegate;
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
            return new DecoratingEntrySet(delegate.entrySet(), consumer);
        }

        @Override
        public void forEach(BiConsumer<? super Object, ? super Object> action) {
            delegate.forEach((k, v) -> {
                LISTENER.get().systemPropertyQueried((String) k, v, consumer);
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
            LISTENER.get().systemPropertyQueried(key, value, consumer);
            if (value == null) {
                return defaultValue;
            }
            return value;
        }

        @Override
        public Object getOrDefault(Object key, Object defaultValue) {
            return getProperty((String) key, (String) defaultValue);
        }

        @Override
        public Object get(Object key) {
            return getProperty((String) key);
        }
    }

    private static class DecoratingEntrySet extends AbstractSet<Map.Entry<Object, Object>> {
        private final Set<Map.Entry<Object, Object>> delegate;
        private final String consumer;

        public DecoratingEntrySet(Set<Map.Entry<Object, Object>> delegate, String consumer) {
            this.delegate = delegate;
            this.consumer = consumer;
        }

        @Override
        public Iterator<Map.Entry<Object, Object>> iterator() {
            Iterator<Map.Entry<Object, Object>> iterator = delegate.iterator();
            return new AbstractIterator<Map.Entry<Object, Object>>() {
                @Override
                protected Map.Entry<Object, Object> computeNext() {
                    if (!iterator.hasNext()) {
                        return endOfData();
                    }
                    Map.Entry<Object, Object> entry = iterator.next();
                    LISTENER.get().systemPropertyQueried((String) entry.getKey(), entry.getValue(), consumer);
                    return entry;
                }
            };
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }

    private static class IntegerSystemPropertyCallSite extends AbstractCallSite {
        public IntegerSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg1.toString(), (Integer) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class LongSystemPropertyCallSite extends AbstractCallSite {
        public LongSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg1.toString(), (Long) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class BooleanSystemPropertyCallSite extends AbstractCallSite {
        public BooleanSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Boolean.class)) {
                return getBoolean(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }
    }

    private static class SystemPropertyCallSite extends AbstractCallSite {
        public SystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg1.toString(), convertToString(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class SystemPropertiesCallSite extends AbstractCallSite {
        public SystemPropertiesCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperties(array.owner.getName());
            } else {
                return super.callGetProperty(receiver);
            }
        }
    }
}
