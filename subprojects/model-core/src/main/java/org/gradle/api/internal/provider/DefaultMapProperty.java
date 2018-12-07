/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class DefaultMapProperty<K, V> extends AbstractProperty<Map<K, V>> implements MapProperty<K, V>, MapProviderInternal<K, V> {

    private static final MapCollectors.EmptyMap EMPTY_MAP = new MapCollectors.EmptyMap();
    private static final MapCollectors.NoValue NO_VALUE = new MapCollectors.NoValue();

    private static final String NULL_KEY_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null key to a property of type %s.", Map.class.getSimpleName());
    private static final String NULL_VALUE_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null value to a property of type %s.", Map.class.getSimpleName());

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final ValueCollector<K> keyCollector;
    private final MapEntryCollector<K, V> entryCollector;
    private MapCollector<K, V> value;
    private final List<MapCollector<K, V>> collectors = new LinkedList<MapCollector<K, V>>();

    public DefaultMapProperty(Class<K> keyType, Class<V> valueType) {
        applyDefaultValue();
        this.keyType = keyType;
        this.valueType = valueType;
        keyCollector = new ValidatingValueCollector<K>(Set.class, keyType, ValueSanitizers.forType(keyType));
        entryCollector = new ValidatingMapEntryCollector<K, V>(keyType, valueType, ValueSanitizers.forType(keyType), ValueSanitizers.forType(valueType));
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public Class<Map<K, V>> getType() {
        return (Class) Map.class;
    }

    @Override
    public Class<K> getKeyType() {
        return keyType;
    }

    @Override
    public Class<V> getValueType() {
        return valueType;
    }

    @Override
    public boolean isPresent() {
        beforeRead();
        if (!value.present()) {
            return false;
        }
        for (MapCollector<K, V> collector : collectors) {
            if (!collector.present()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Map<K, V> get() {
        beforeRead();
        Map<K, V> entries = new LinkedHashMap<K, V>(1 + collectors.size());
        value.collectInto(entryCollector, entries);
        for (MapCollector<K, V> collector : collectors) {
            collector.collectInto(entryCollector, entries);
        }
        return ImmutableMap.copyOf(entries);
    }

    @Nullable
    @Override
    public Map<K, V> getOrNull() {
        beforeRead();
        return doGetOrNull();
    }

    @Nullable
    private Map<K, V> doGetOrNull() {
        Map<K, V> entries = new LinkedHashMap<K, V>(1 + collectors.size());
        if (!value.maybeCollectInto(entryCollector, entries)) {
            return null;
        }
        for (MapCollector<K, V> collector : collectors) {
            if (!collector.maybeCollectInto(entryCollector, entries)) {
                return null;
            }
        }
        return ImmutableMap.copyOf(entries);
    }

    @Override
    public Provider<V> getting(final K key) {
        return new DefaultProvider<V>(new Callable<V>() {
            @Override
            @Nullable
            public V call() {
                Map<K, V> dest = new LinkedHashMap<K, V>();
                for (int i = collectors.size() - 1; i >= 0; i--) {
                    if (collectors.get(i).maybeCollectInto(entryCollector, dest)) {
                        V value = dest.get(key);
                        if (value != null) {
                            return value;
                        }
                    } else {
                        return null;
                    }
                    dest.clear();
                }
                if (value.maybeCollectInto(entryCollector, dest)) {
                    V value = dest.get(key);
                    if (value != null) {
                        return value;
                    }
                }
                return null;
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapProperty<K, V> empty() {
        if (beforeMutate()) {
            set((MapCollector<K, V>) EMPTY_MAP);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setFromAnyValue(@Nullable Object object) {
        if (object == null || object instanceof Map<?, ?>) {
            set((Map) object);
        } else if (object instanceof Provider<?>) {
            set((Provider) object);
        } else {
            throw new IllegalArgumentException(String.format(
                "Cannot set the value of a property of type %s using an instance of type %s.", Map.class.getName(), object.getClass().getName()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void set(@Nullable Map<? extends K, ? extends V> entries) {
        if (!beforeMutate()) {
            return;
        }
        if (entries != null) {
            set(new MapCollectors.EntriesFromMap<K, V>(entries));
        } else {
            set((MapCollector<K, V>) NO_VALUE);
        }
    }

    @Override
    public void set(Provider<? extends Map<? extends K, ? extends V>> provider) {
        if (!beforeMutate()) {
            return;
        }
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = checkMapProvider(provider);
        set(new MapCollectors.EntriesFromMapProvider<K, V>(p));
    }

    private void set(MapCollector<K, V> collector) {
        collectors.clear();
        value = collector;
        afterMutate();
    }

    @Override
    public void put(K key, V value) {
        Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
        Preconditions.checkNotNull(value, NULL_VALUE_FORBIDDEN_MESSAGE);
        if (!beforeMutate()) {
            return;
        }
        addCollector(new MapCollectors.SingleEntry<K, V>(key, value));
    }

    @Override
    public void put(K key, Provider<? extends V> providerOfValue) {
        Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
        Preconditions.checkNotNull(providerOfValue, NULL_VALUE_FORBIDDEN_MESSAGE);
        if (!beforeMutate()) {
            return;
        }
        ProviderInternal<? extends V> p = Providers.internal(providerOfValue);
        if (p.getType() != null && !valueType.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot add an entry to a property of type %s with values of type %s using a provider of type %s.",
                Map.class.getName(), valueType.getName(), p.getType().getName()));
        }
        addCollector(new MapCollectors.EntryWithValueFromProvider<K, V>(key, p));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> entries) {
        if (!beforeMutate()) {
            return;
        }
        addCollector(new MapCollectors.EntriesFromMap<K, V>(entries));
    }

    @Override
    public void putAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
        if (!beforeMutate()) {
            return;
        }
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = checkMapProvider(provider);
        addCollector(new MapCollectors.EntriesFromMapProvider<K, V>(p));
    }

    private void addCollector(MapCollector<K, V> collector) {
        collectors.add(collector);
        afterMutate();
    }

    @SuppressWarnings("unchecked")
    private ProviderInternal<? extends Map<? extends K, ? extends V>> checkMapProvider(@Nullable Provider<? extends Map<? extends K, ? extends V>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = Providers.internal(provider);
        if (p.getType() != null && !Map.class.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using a provider of type %s.",
                Map.class.getName(), p.getType().getName()));
        }
        if (p instanceof MapProviderInternal) {
            Class<? extends K> providerKeyType = ((MapProviderInternal<? extends K, ? extends V>) p).getKeyType();
            Class<? extends V> providerValueType = ((MapProviderInternal<? extends K, ? extends V>) p).getValueType();
            if (!keyType.isAssignableFrom(providerKeyType) || !valueType.isAssignableFrom(providerValueType)) {
                throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s with key type %s and value type %s " +
                        "using a provider with key type %s and value type %s.", Map.class.getName(), keyType.getName(), valueType.getName(),
                    providerKeyType.getName(), providerValueType.getName()));
            }
        }
        return p;
    }

    @Override
    public MapProperty<K, V> convention(Map<? extends K, ? extends V> value) {
        if (shouldApplyConvention()) {
            this.value = new MapCollectors.EntriesFromMap<K, V>(value);
            collectors.clear();
        }
        return this;
    }

    @Override
    public MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> valueProvider) {
        if (shouldApplyConvention()) {
            this.value = new MapCollectors.EntriesFromMapProvider<K, V>(Providers.internal(valueProvider));
            collectors.clear();
        }
        return this;
    }

    @Override
    public Provider<Set<K>> keySet() {
        return new KeySetProvider();
    }

    @Override
    public String toString() {
        List<String> values = new ArrayList<String>(1 + collectors.size());
        values.add(value.toString());
        for (MapCollector<K, V> collector : collectors) {
            values.add(collector.toString());
        }
        return String.format("Map(%s->%s, %s)", keyType.getSimpleName().toLowerCase(), valueType.getSimpleName(), values);
    }

    @Override
    protected void applyDefaultValue() {
        value = (MapCollector<K, V>) EMPTY_MAP;
        collectors.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void makeFinal() {
        Map<K, V> entries = doGetOrNull();
        if (entries != null) {
            if (entries.isEmpty()) {
                set((MapCollector<K, V>) EMPTY_MAP);
            } else {
                set(new MapCollectors.EntriesFromMap<K, V>(entries));
            }
        } else {
            set((MapCollector<K, V>) NO_VALUE);
        }
    }

    private class KeySetProvider extends AbstractReadOnlyProvider<Set<K>> {

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public Class<Set<K>> getType() {
            return (Class) Set.class;
        }

        @Override
        public Set<K> get() {
            beforeRead();
            Set<K> keys = new LinkedHashSet<K>(1 + collectors.size());
            value.collectKeysInto(keyCollector, keys);
            for (MapCollector<K, V> collector : collectors) {
                collector.collectKeysInto(keyCollector, keys);
            }
            return ImmutableSet.copyOf(keys);
        }

        @Nullable
        @Override
        public Set<K> getOrNull() {
            beforeRead();
            Set<K> keys = new LinkedHashSet<K>(1 + collectors.size());
            if (!value.maybeCollectKeysInto(keyCollector, keys)) {
                return null;
            }
            for (MapCollector<K, V> collector : collectors) {
                if (!collector.maybeCollectKeysInto(keyCollector, keys)) {
                    return null;
                }
            }
            return ImmutableSet.copyOf(keys);
        }
    }
}
