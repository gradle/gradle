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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultMapProperty<K, V> extends AbstractProperty<Map<K, V>, MapSupplier<K, V>> implements MapProperty<K, V>, MapProviderInternal<K, V> {
    private static final String NULL_KEY_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null key to a property of type %s.", Map.class.getSimpleName());
    private static final String NULL_VALUE_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null value to a property of type %s.", Map.class.getSimpleName());

    private static final MapSupplier<Object, Object> NO_VALUE = new NoValueSupplier<>(Value.missing());

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final ValueCollector<K> keyCollector;
    private final MapEntryCollector<K, V> entryCollector;
    private MapSupplier<K, V> defaultValue = emptySupplier();

    public DefaultMapProperty(PropertyHost propertyHost, Class<K> keyType, Class<V> valueType) {
        super(propertyHost);
        this.keyType = keyType;
        this.valueType = valueType;
        keyCollector = new ValidatingValueCollector<>(Set.class, keyType, ValueSanitizers.forType(keyType));
        entryCollector = new ValidatingMapEntryCollector<>(keyType, valueType, ValueSanitizers.forType(keyType), ValueSanitizers.forType(valueType));
        init(defaultValue, noValueSupplier());
    }

    private MapSupplier<K, V> emptySupplier() {
        return new EmptySupplier();
    }

    private MapSupplier<K, V> noValueSupplier() {
        return Cast.uncheckedCast(NO_VALUE);
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
    public Class<?> publicType() {
        return MapProperty.class;
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.MapPropertyManagedFactory.FACTORY_ID;
    }

    @Override
    public Provider<V> getting(final K key) {
        return new EntryProvider(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapProperty<K, V> empty() {
        setSupplier(emptySupplier());
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
        if (entries == null) {
            discardValue();
            defaultValue = noValueSupplier();
        } else {
            setSupplier(new CollectingSupplier(new MapCollectors.EntriesFromMap<>(entries)));
        }
    }

    @Override
    public void set(Provider<? extends Map<? extends K, ? extends V>> provider) {
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = checkMapProvider(provider);
        setSupplier(new CollectingSupplier(new MapCollectors.EntriesFromMapProvider<>(p)));
    }

    @Override
    public MapProperty<K, V> value(@Nullable Map<? extends K, ? extends V> entries) {
        set(entries);
        return this;
    }

    @Override
    public MapProperty<K, V> value(Provider<? extends Map<? extends K, ? extends V>> provider) {
        set(provider);
        return this;
    }

    @Override
    public void put(K key, V value) {
        Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
        Preconditions.checkNotNull(value, NULL_VALUE_FORBIDDEN_MESSAGE);
        addCollector(new MapCollectors.SingleEntry<>(key, value));
    }

    @Override
    public void put(K key, Provider<? extends V> providerOfValue) {
        Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
        Preconditions.checkNotNull(providerOfValue, NULL_VALUE_FORBIDDEN_MESSAGE);
        ProviderInternal<? extends V> p = Providers.internal(providerOfValue);
        if (p.getType() != null && !valueType.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot add an entry to a property of type %s with values of type %s using a provider of type %s.",
                Map.class.getName(), valueType.getName(), p.getType().getName()));
        }
        addCollector(new MapCollectors.EntryWithValueFromProvider<>(key, p));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> entries) {
        addCollector(new MapCollectors.EntriesFromMap<>(entries));
    }

    @Override
    public void putAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = checkMapProvider(provider);
        addCollector(new MapCollectors.EntriesFromMapProvider<>(p));
    }

    private void addCollector(MapCollector<K, V> collector) {
        assertCanMutate();
        setSupplier(getExplicitValue(defaultValue).plus(collector));
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
    public MapProperty<K, V> convention(@Nullable Map<? extends K, ? extends V> value) {
        if (value == null) {
            setConvention(noValueSupplier());
        } else {
            setConvention(new CollectingSupplier(new MapCollectors.EntriesFromMap<>(value)));
        }
        return this;
    }

    @Override
    public MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> valueProvider) {
        setConvention(new CollectingSupplier(new MapCollectors.EntriesFromMapProvider<>(Providers.internal(valueProvider))));
        return this;
    }

    public void fromState(ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value) {
        if (value.isMissing()) {
            setSupplier(noValueSupplier());
        } else if (value.isFixedValue()) {
            setSupplier(new FixedSuppler<>(Cast.uncheckedNonnullCast(value.getFixedValue())));
        } else {
            setSupplier(new CollectingSupplier(new MapCollectors.EntriesFromMapProvider<>(value.getChangingValue())));
        }
    }

    @Override
    public Provider<Set<K>> keySet() {
        return new KeySetProvider();
    }

    @Override
    protected String describeContents() {
        return String.format("Map(%s->%s, %s)", keyType.getSimpleName().toLowerCase(), valueType.getSimpleName(), getSupplier().toString());
    }

    @Override
    protected Value<? extends Map<K, V>> calculateValueFrom(MapSupplier<K, V> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected MapSupplier<K, V> finalValue(MapSupplier<K, V> value, ValueConsumer consumer) {
        Value<? extends Map<K, V>> result = value.calculateValue(consumer);
        if (!result.isMissing()) {
            Map<K, V> entries = result.get();
            return new FixedSuppler<>(entries);
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return new NoValueSupplier<>(result);
        }
    }

    @Override
    protected ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue(MapSupplier<K, V> value) {
        return value.calculateOwnExecutionTimeValue();
    }

    private class EntryProvider extends AbstractMinimalProvider<V> {
        private final K key;

        public EntryProvider(K key) {
            this.key = key;
        }

        @Nullable
        @Override
        public Class<V> getType() {
            return valueType;
        }

        @Override
        protected Value<? extends V> calculateOwnValue(ValueConsumer consumer) {
            Value<? extends Map<K, V>> result = DefaultMapProperty.this.calculateOwnValue(consumer);
            if (result.isMissing()) {
                return result.asType();
            }
            return Value.ofNullable(result.get().get(key));
        }
    }

    private class KeySetProvider extends AbstractMinimalProvider<Set<K>> {
        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public Class<Set<K>> getType() {
            return (Class) Set.class;
        }

        @Override
        protected Value<? extends Set<K>> calculateOwnValue(ValueConsumer consumer) {
            beforeRead(consumer);
            return getSupplier().calculateKeys(consumer);
        }
    }

    private static class NoValueSupplier<K, V> implements MapSupplier<K, V> {
        private final Value<? extends Map<K, V>> value;

        public NoValueSupplier(Value<? extends Map<K, V>> value) {
            this.value = value.asType();
            assert value.isMissing();
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return false;
        }

        @Override
        public Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer) {
            return value;
        }

        @Override
        public Value<? extends Set<K>> calculateKeys(ValueConsumer consumer) {
            return value.asType();
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> collector) {
            // nothing + something = nothing
            return this;
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue() {
            return ExecutionTimeValue.missing();
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    private class EmptySupplier implements MapSupplier<K, V> {
        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer) {
            return Value.of(ImmutableMap.of());
        }

        @Override
        public Value<? extends Set<K>> calculateKeys(ValueConsumer consumer) {
            return Value.of(ImmutableSet.of());
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> collector) {
            // empty + something = something
            return new CollectingSupplier(collector);
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(ImmutableMap.of());
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.noProducer();
        }
    }

    private static class FixedSuppler<K, V> implements MapSupplier<K, V> {
        private final Map<K, V> entries;

        public FixedSuppler(Map<K, V> entries) {
            this.entries = entries;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer) {
            return Value.of(entries);
        }

        @Override
        public Value<? extends Set<K>> calculateKeys(ValueConsumer consumer) {
            return Value.of(entries.keySet());
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> collector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(entries);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    private class CollectingSupplier implements MapSupplier<K, V> {
        private final MapCollector<K, V> collector;

        public CollectingSupplier(MapCollector<K, V> collector) {
            this.collector = collector;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return collector.calculatePresence(consumer);
        }

        @Override
        public Value<? extends Set<K>> calculateKeys(ValueConsumer consumer) {
            // TODO - don't make a copy when the collector already produces an immutable collection
            ImmutableSet.Builder<K> builder = ImmutableSet.builder();
            Value<Void> result = collector.collectKeys(consumer, keyCollector, builder);
            if (result.isMissing()) {
                return result.asType();
            }
            return Value.of(ImmutableSet.copyOf(builder.build()));
        }

        @Override
        public Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer) {
            // TODO - don't make a copy when the collector already produces an immutable collection
            // Cannot use ImmutableMap.Builder here, as it does not allow multiple entries with the same key, however the contract
            // for MapProperty allows a provider to override the entries of earlier providers and so there can be multiple entries
            // with the same key
            Map<K, V> entries = new LinkedHashMap<>();
            Value<Void> result = collector.collectEntries(consumer, entryCollector, entries);
            if (result.isMissing()) {
                return result.asType();
            }
            return Value.of(ImmutableMap.copyOf(entries));
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> collector) {
            return new CollectingSupplier(new PlusCollector<>(this.collector, collector));
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue() {
            List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values = new ArrayList<>();
            collector.calculateExecutionTimeValue(values::add);
            boolean fixed = true;
            boolean changingContent = false;
            for (ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value : values) {
                if (value.isMissing()) {
                    return ExecutionTimeValue.missing();
                }
                if (value.isChangingValue()) {
                    fixed = false;
                } else if (value.hasChangingContent()) {
                    changingContent = true;
                }
            }
            if (fixed) {
                Map<K, V> entries = new LinkedHashMap<>();
                for (ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value : values) {
                    entries.putAll(value.getFixedValue());
                }
                ExecutionTimeValue<Map<K, V>> value = ExecutionTimeValue.fixedValue(ImmutableMap.copyOf(entries));
                if (changingContent) {
                    return value.withChangingContent();
                } else {
                    return value;
                }
            }
            List<ProviderInternal<? extends Map<? extends K, ? extends V>>> providers = new ArrayList<>();
            for (ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value : values) {
                providers.add(value.toProvider());
            }
            return ExecutionTimeValue.changingValue(new CollectingProvider<K, V>(providers));
        }

        @Override
        public ValueProducer getProducer() {
            return collector.getProducer();
        }
    }

    private static class CollectingProvider<K, V> extends AbstractMinimalProvider<Map<K, V>> {
        private final List<ProviderInternal<? extends Map<? extends K, ? extends V>>> providers;

        public CollectingProvider(List<ProviderInternal<? extends Map<? extends K, ? extends V>>> providers) {
            this.providers = providers;
        }

        @Nullable
        @Override
        public Class<Map<K, V>> getType() {
            return Cast.uncheckedCast(Map.class);
        }

        @Override
        protected Value<? extends Map<K, V>> calculateOwnValue(ValueConsumer consumer) {
            Map<K, V> entries = new LinkedHashMap<>();
            for (ProviderInternal<? extends Map<? extends K, ? extends V>> provider : providers) {
                Value<? extends Map<? extends K, ? extends V>> value = provider.calculateValue(consumer);
                if (value.isMissing()) {
                    return Value.missing();
                }
                entries.putAll(value.get());
            }
            return Value.of(ImmutableMap.copyOf(entries));
        }
    }

    private static class PlusCollector<K, V> implements MapCollector<K, V> {
        private final MapCollector<K, V> left;
        private final MapCollector<K, V> right;

        public PlusCollector(MapCollector<K, V> left, MapCollector<K, V> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return left.calculatePresence(consumer) && right.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<Void> result = left.collectEntries(consumer, collector, dest);
            if (result.isMissing()) {
                return result;
            }
            return right.collectEntries(consumer, collector, dest);
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            Value<Void> result = left.collectKeys(consumer, collector, dest);
            if (result.isMissing()) {
                return result;
            }
            return right.collectKeys(consumer, collector, dest);
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            left.calculateExecutionTimeValue(visitor);
            right.calculateExecutionTimeValue(visitor);
        }

        @Override
        public ValueProducer getProducer() {
            return left.getProducer().plus(right.getProducer());
        }
    }
}
