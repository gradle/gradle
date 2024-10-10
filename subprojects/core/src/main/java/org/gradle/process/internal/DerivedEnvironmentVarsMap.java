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

package org.gradle.process.internal;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Iterators;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableSupplier;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A set of environment variables derived from some base.
 * This class is configuration-cache aware: instead of storing mutated result, it stores base and mutations separately and replays mutation upon loading from the cache.
 * This is handy when the base is a "changing" supplier, so user mutations can be replayed on top of the actual environment.
 * <p>
 * So far, only a limited set of mutations is replayed:
 * <ul>
 *     <li>{@link #put(String, Object)}, {@link #putAll(Map)}</li>
 *     <li>{@link #remove(Object)} - only the single argument version</li>
 *     <li>{@link #clear()}</li>
 *     <li>variations of the above operating on {@link #entrySet()}, {@link #keySet()}, and {@link #values()}</li>
 * </ul>
 * More complex uses, like {@link #replace(Object, Object)} do not work properly.
 * For example, {@code replace("FOO", "bar")} will add the mapping regardless of {@code "FOO"} presence when replaying.
 */
@NonNullApi
class DerivedEnvironmentVarsMap extends ForwardingMap<String, Object> implements Serializable {
    // There are plenty of ways to modify this map based on the values coming from base: replace, computeIfAbsent, just reading the map contents.
    // To support that, everything that depends on the current environment should either be replayed or made a configuration input.
    // The former is complex, and the latter is probably too intrusive (especially considering that put and remove also expose the map contents).
    // After the PAPI migration, users will have a proper way to rebuild the environment with a ValueSource, so the proper tracking can be implemented then.

    // Enum singleton implementation. Represents a removed environment variable.
    @NonNullApi
    private enum Sentinel {
        REMOVED
    }

    // The source of the base environment, typically a changing provider.
    // Null represent an absent base, this can happen if the user clears the map while configuring.
    @Nullable
    private SerializableSupplier<Map<String, String>> base;
    // The recording of user-applied updates to the base environment, including removals.
    private final Map<String, Object> updates = new HashMap<>();

    // The currently configured environment, typically a combination of base and updates.
    // This is not stored in the configuration cache, but lazily reconstructed upon first read after restoring.
    @Nullable
    private transient Map<String, Object> environment;

    DerivedEnvironmentVarsMap(SerializableSupplier<Map<String, String>> base) {
        this.base = base;
    }

    @Override
    protected Map<String, Object> delegate() {
        Map<String, Object> delegate = environment;
        if (delegate == null) {
            environment = delegate = buildEnvironment();
        }

        return delegate;
    }

    private Map<String, Object> buildEnvironment() {
        Supplier<Map<String, String>> base = this.base;
        Map<String, Object> updated = new HashMap<>(base != null ? base.get() : Collections.emptyMap());
        updates.forEach((key, updatedValue) -> {
            if (updatedValue == Sentinel.REMOVED) {
                updated.remove(key);
            } else {
                updated.put(key, updatedValue);
            }
        });
        return updated;
    }

    @Nullable
    @Override
    public Object remove(@Nullable Object key) {
        if (key instanceof String) {
            updates.put((String) key, Sentinel.REMOVED);
        }
        return super.remove(key);
    }

    @Override
    public void clear() {
        base = null;
        updates.clear();
        super.clear();
    }

    @Nullable
    @Override
    public Object put(String key, Object value) {
        updates.put(key, value);
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        updates.putAll(map);
        super.putAll(map);
    }

    @Override
    public Set<String> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<Object> values() {
        return new ValuesCollection();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return new EntrySet();
    }

    @NonNullApi
    private class EntrySet extends AbstractSet<Map.Entry<String, Object>> {
        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return new EntrySetIterator();
        }

        @Override
        public int size() {
            return delegate().size();
        }

        @Override
        public void clear() {
            // Detach the base
            DerivedEnvironmentVarsMap.this.clear();
        }
    }

    @NonNullApi
    private class EntrySetIterator implements Iterator<Map.Entry<String, Object>> {
        private final Iterator<Map.Entry<String, Object>> iter = delegate().entrySet().iterator();

        @Nullable
        private String lastKey;

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Map.Entry<String, Object> next() {
            Map.Entry<String, Object> nextEntry = iter.next();
            lastKey = nextEntry.getKey();
            return new Entry(nextEntry);
        }

        @Override
        public void remove() {
            // Order is important, let the base iterator handle state checks.
            iter.remove();
            updates.put(lastKey, Sentinel.REMOVED);
        }
    }

    @NonNullApi
    private class Entry implements Map.Entry<String, Object> {
        private final Map.Entry<String, Object> entry;

        Entry(Map.Entry<String, Object> entry) {
            this.entry = entry;
        }

        @Override
        public String getKey() {
            return entry.getKey();
        }

        @Override
        @Nullable
        public Object getValue() {
            return entry.getValue();
        }

        @Override
        @Nullable
        public Object setValue(@Nullable Object value) {
            updates.put(getKey(), value);
            return entry.setValue(value);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Map.Entry<?, ?>)) {
                return false;
            }
            Map.Entry<?, ?> e = Cast.uncheckedCast(o);
            return Objects.equals(entry.getKey(), e.getKey()) && Objects.equals(entry.getValue(), e.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(entry);
        }
    }

    @NonNullApi
    private class KeySet extends AbstractSet<String> {
        @Override
        public Iterator<String> iterator() {
            return Iterators.transform(entrySet().iterator(), Map.Entry::getKey);
        }

        @Override
        public int size() {
            return delegate().size();
        }

        @Override
        public void clear() {
            // Detach the base
            DerivedEnvironmentVarsMap.this.clear();
        }
    }

    @NonNullApi
    private class ValuesCollection extends AbstractCollection<Object> {
        @Override
        public Iterator<Object> iterator() {
            return Iterators.transform(entrySet().iterator(), Map.Entry::getValue);
        }

        @Override
        public int size() {
            return delegate().size();
        }

        @Override
        public void clear() {
            // Detach the base
            DerivedEnvironmentVarsMap.this.clear();
        }
    }
}
