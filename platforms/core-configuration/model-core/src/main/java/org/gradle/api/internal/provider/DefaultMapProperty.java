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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.MapCollectors.EntriesFromMap;
import org.gradle.api.internal.provider.MapCollectors.EntriesFromMapProvider;
import org.gradle.api.internal.provider.MapCollectors.EntryWithValueFromProvider;
import org.gradle.api.internal.provider.MapCollectors.SingleEntry;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * The implementation for {@link MapProperty}.
 * <p>
 *     Value suppliers for map properties are implementations of {@link MapSupplier}.
 * </p>
 * <p>
 *     Increments to map property values are implementations of {@link MapCollector}.
 * </p>
 *
 * This class mimics much of the behavior {@link AbstractCollectionProperty} provides for regular collections
 * but for maps. Read that class' documentation to better understand the roles of {@link MapSupplier} and {@link MapCollector}.
 *
 * @param <K> the type of entry key
 * @param <V> the type of entry value
 */
public class DefaultMapProperty<K, V> extends AbstractProperty<Map<K, V>, MapSupplier<K, V>> implements MapProperty<K, V>, MapProviderInternal<K, V>, MapPropertyInternal<K, V> {
    private static final String NULL_KEY_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null key to a property of type %s.", Map.class.getSimpleName());
    private static final String NULL_VALUE_FORBIDDEN_MESSAGE = String.format("Cannot add an entry with a null value to a property of type %s.", Map.class.getSimpleName());

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
        init();
    }

    private void init() {
        defaultValue = emptySupplier();
        init(defaultValue, noValueSupplier());
    }

    @Override
    public MapSupplier<K, V> getDefaultValue() {
        return defaultValue;
    }

    @Override
    protected MapSupplier<K, V> getDefaultConvention() {
        return noValueSupplier();
    }

    @Override
    protected boolean isDefaultConvention() {
        return isNoValueSupplier(getConventionSupplier());
    }

    private MapSupplier<K, V> emptySupplier() {
        return new EmptySupplier();
    }

    private MapSupplier<K, V> noValueSupplier() {
        return uncheckedCast(new NoValueSupplier(Value.missing()));
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
            unsetValueAndDefault();
        } else {
            setSupplier(new CollectingSupplier(new EntriesFromMap<>(entries), false));
        }
    }

    @Override
    public void set(Provider<? extends Map<? extends K, ? extends V>> provider) {
        setSupplier(new CollectingSupplier(new MapCollectors.EntriesFromMapProvider<>(checkMapProvider(provider)), false));
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
        getConfigurer().put(key, value);
    }

    @Override
    public void put(K key, Provider<? extends V> providerOfValue) {
        getConfigurer().put(key, providerOfValue);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> entries) {
        getConfigurer().putAll(entries);
    }

    @Override
    public void putAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
        getConfigurer().putAll(provider);
    }

    @Override
    public void insert(K key, Provider<? extends V> providerOfValue) {
        withActualValue(it -> it.put(key, providerOfValue));
    }

    @Override
    public void insert(K key, V value) {
        withActualValue(it -> it.put(key, value));
    }

    @Override
    public void insertAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
        withActualValue(it -> it.putAll(provider));
    }

    @Override
    public void insertAll(Map<? extends K, ? extends V> entries) {
        withActualValue(it -> it.putAll(entries));
    }

    private void addExplicitCollector(MapCollector<K, V> collector, boolean ignoreAbsent) {
        assertCanMutate();
        MapSupplier<K, V> explicitValue = getExplicitValue(defaultValue).absentIgnoringIfNeeded(ignoreAbsent);
        setSupplier(explicitValue.plus(collector.absentIgnoringIfNeeded(ignoreAbsent)));
    }

    private Configurer getConfigurer() {
        return getConfigurer(false);
    }

    private Configurer getConfigurer(boolean ignoreAbsent) {
        return new Configurer(ignoreAbsent);
    }

    protected void withActualValue(Action<Configurer> action) {
        setToConventionIfUnset();
        action.execute(getConfigurer(true));
    }

    private boolean isNoValueSupplier(MapSupplier<K, V> valueSupplier) {
        return valueSupplier instanceof DefaultMapProperty.NoValueSupplier;
    }

    private ProviderInternal<? extends Map<? extends K, ? extends V>> checkMapProvider(@Nullable Provider<? extends Map<? extends K, ? extends V>> provider) {
        return checkMapProvider("value", provider);
    }

    @SuppressWarnings("unchecked")
    private ProviderInternal<? extends Map<? extends K, ? extends V>> checkMapProvider(String valueKind, @Nullable Provider<? extends Map<? extends K, ? extends V>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException(String.format("Cannot set the %s of a property using a null provider.", valueKind));
        }
        ProviderInternal<? extends Map<? extends K, ? extends V>> p = Providers.internal(provider);
        if (p.getType() != null && !Map.class.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the %s of a property of type %s using a provider of type %s.",
                valueKind,
                Map.class.getName(), p.getType().getName()));
        }
        if (p instanceof MapProviderInternal) {
            Class<? extends K> providerKeyType = ((MapProviderInternal<? extends K, ? extends V>) p).getKeyType();
            Class<? extends V> providerValueType = ((MapProviderInternal<? extends K, ? extends V>) p).getValueType();
            if (!keyType.isAssignableFrom(providerKeyType) || !valueType.isAssignableFrom(providerValueType)) {
                throw new IllegalArgumentException(String.format("Cannot set the %s of a property of type %s with key type %s and value type %s " +
                        "using a provider with key type %s and value type %s.", valueKind, Map.class.getName(), keyType.getName(), valueType.getName(),
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
            setConvention(new CollectingSupplier(new EntriesFromMap<>(value), false));
        }
        return this;
    }

    @Override
    public MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> valueProvider) {
        setConvention(new CollectingSupplier(new EntriesFromMapProvider<>(Providers.internal(valueProvider)), false));
        return this;
    }

    @Override
    public MapProperty<K, V> unsetConvention() {
        discardConvention();
        return this;
    }

    @Override
    public MapProperty<K, V> unset() {
        return Cast.uncheckedNonnullCast(super.unset());
    }

    private void unsetValueAndDefault() {
        // assign no-value default before restoring to it
        defaultValue = noValueSupplier();
        unset();
    }

    public void fromState(ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value) {
        if (value.isMissing()) {
            setSupplier(noValueSupplier());
        } else if (value.hasFixedValue()) {
            setSupplier(new FixedSupplier(uncheckedNonnullCast(value.getFixedValue()), uncheckedCast(value.getSideEffect())));
        } else {
            CollectingProvider<K, V> asCollectingProvider = uncheckedNonnullCast(value.getChangingValue());
            setSupplier(new CollectingSupplier(new EntriesFromMapProvider<>(asCollectingProvider)));
        }
    }

    @Override
    public Provider<Set<K>> keySet() {
        return new KeySetProvider();
    }

    public void replace(Transformer<? extends @org.jetbrains.annotations.Nullable Provider<? extends Map<? extends K, ? extends V>>, ? super Provider<Map<K, V>>> transformation) {
        Provider<? extends Map<? extends K, ? extends V>> newValue = transformation.transform(shallowCopy());
        if (newValue != null) {
            set(newValue);
        } else {
            set((Map<? extends K, ? extends V>) null);
        }
    }

    @Override
    protected String describeContents() {
        return String.format("Map(%s->%s, %s)", keyType.getSimpleName(), valueType.getSimpleName(), describeValue());
    }

    @Override
    protected Value<? extends Map<K, V>> calculateValueFrom(EvaluationScopeContext context, MapSupplier<K, V> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected MapSupplier<K, V> finalValue(EvaluationScopeContext context, MapSupplier<K, V> value, ValueConsumer consumer) {
        Value<? extends Map<K, V>> result = value.calculateValue(consumer);
        if (!result.isMissing()) {
            return new FixedSupplier(result.getWithoutSideEffect(), uncheckedCast(result.getSideEffect()));
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return new NoValueSupplier(result);
        }
    }

    @Override
    protected ExecutionTimeValue<? extends Map<K, V>> calculateOwnExecutionTimeValue(EvaluationScopeContext context, MapSupplier<K, V> value) {
        return value.calculateExecutionTimeValue();
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
            Value<? extends V> resultValue = Value.ofNullable(result.getWithoutSideEffect().get(key));
            return resultValue.withSideEffect(SideEffect.fixedFrom(result));
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
            try (EvaluationScopeContext context = DefaultMapProperty.this.openScope()) {
                beforeRead(context, consumer);
                return getSupplier(context).calculateKeys(consumer);
            }
        }
    }

    private class NoValueSupplier implements MapSupplier<K, V> {
        private final Value<? extends Map<K, V>> value;

        public NoValueSupplier(Value<? extends Map<K, V>> value) {
            this.value = value.asType();
            assert value.isMissing();
        }

        @Override
        public MapSupplier<K, V> absentIgnoring() {
            return emptySupplier();
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
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.missing();
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    private class EmptySupplier implements MapSupplier<K, V> {
        @Override
        public MapSupplier<K, V> absentIgnoring() {
            return this;
        }

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
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(ImmutableMap.of());
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.noProducer();
        }

        @Override
        public String toString() {
            return "{}";
        }
    }

    private class FixedSupplier implements MapSupplier<K, V> {
        private final Map<K, V> entries;
        private final SideEffect<? super Map<K, V>> sideEffect;

        public FixedSupplier(Map<K, V> entries, @Nullable SideEffect<? super Map<K, V>> sideEffect) {
            this.entries = entries;
            this.sideEffect = sideEffect;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends Map<K, V>> calculateValue(ValueConsumer consumer) {
            return Value.of(entries).withSideEffect(sideEffect);
        }

        @Override
        public Value<? extends Set<K>> calculateKeys(ValueConsumer consumer) {
            return Value.of(entries.keySet());
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> collector) {
            MapCollector<K, V> left = new FixedValueCollector<>(entries, sideEffect);
            PlusCollector<K, V> newCollector = new PlusCollector<>(left, collector);
            return new CollectingSupplier(newCollector);
        }

        @Override
        public MapSupplier<K, V> absentIgnoring() {
            return this;
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(entries).withSideEffect(sideEffect);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public String toString() {
            return entries.toString();
        }
    }

    private class CollectingSupplier implements MapSupplier<K, V> {
        private final MapCollector<K, V> collector;
        // TODO-RC: can we get rid of this? Can we only keep this in Collectors? Changing execution time value is the only case that needs this.
        private final boolean ignoreAbsent;

        public CollectingSupplier(MapCollector<K, V> collector, boolean ignoreAbsent) {
            this.collector = collector;
            this.ignoreAbsent = ignoreAbsent;
        }

        public CollectingSupplier(MapCollector<K, V> collector) {
            this(collector, false);
        }

        @Override
        public MapSupplier<K, V> absentIgnoring() {
            return ignoreAbsent ? this : new CollectingSupplier(collector, true);
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
            return Value.of(ImmutableSet.copyOf(builder.build())).withSideEffect(SideEffect.fixedFrom(result));
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
            return Value.of(ImmutableMap.copyOf(entries)).withSideEffect(SideEffect.fixedFrom(result));
        }

        @Override
        public MapSupplier<K, V> plus(MapCollector<K, V> addedCollector) {
            MapCollector<K, V> left = this.collector.absentIgnoringIfNeeded(ignoreAbsent);
            MapCollector<K, V> right = addedCollector;
            PlusCollector<K, V> newCollector = new PlusCollector<>(left, right);
            return new CollectingSupplier(newCollector);
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> execTimeValues = collectExecutionTimeValues();
            ExecutionTimeValue<Map<K, V>> fixedOrMissing = fixedOrMissingValueOf(execTimeValues);
            return fixedOrMissing != null
                ? fixedOrMissing
                : ExecutionTimeValue.changingValue(new CollectingProvider<>(execTimeValues));
        }

        /**
         * Try to simplify the set of execution values to either a missing value or a fixed value.
         */
        @Nullable
        private ExecutionTimeValue<Map<K, V>> fixedOrMissingValueOf(List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values) {
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
                SideEffectBuilder<? super Map<K, V>> sideEffectBuilder = SideEffect.builder();
                ImmutableMap<K, V> entries = collectEntries(values, sideEffectBuilder);
                return maybeChangingContent(ExecutionTimeValue.fixedValue(entries), changingContent)
                    .withSideEffect(sideEffectBuilder.build());
            }
            return null;
        }

        private List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> collectExecutionTimeValues() {
            List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values = new ArrayList<>();
            collector.calculateExecutionTimeValue(values::add);
            return values;
        }

        private ImmutableMap<K, V> collectEntries(List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values, SideEffectBuilder<? super Map<K, V>> sideEffectBuilder) {
            Map<K, V> entries = new LinkedHashMap<>();
            for (ExecutionTimeValue<? extends Map<? extends K, ? extends V>> value : values) {
                entryCollector.addAll(value.getFixedValue().entrySet(), entries);
                sideEffectBuilder.add(SideEffect.fixedFrom(value));
            }
            return ImmutableMap.copyOf(entries);
        }

        private ExecutionTimeValue<Map<K, V>> maybeChangingContent(ExecutionTimeValue<Map<K, V>> value, boolean changingContent) {
            return changingContent ? value.withChangingContent() : value;
        }

        @Override
        public ValueProducer getProducer() {
            return collector.getProducer();
        }

        @Override
        public String toString() {
            return collector.toString();
        }
    }

    private static class CollectingProvider<K, V> extends AbstractMinimalProvider<Map<K, V>> {
        private final List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values;

        public CollectingProvider(List<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> values) {
            this.values = values;
        }

        @Nullable
        @Override
        public Class<Map<K, V>> getType() {
            return uncheckedCast(Map.class);
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.changingValue(this);
        }

        @Override
        protected Value<? extends Map<K, V>> calculateOwnValue(ValueConsumer consumer) {
            Map<K, V> entries = new LinkedHashMap<>();
            SideEffectBuilder<? super Map<K, V>> sideEffectBuilder = SideEffect.builder();
            for (ExecutionTimeValue<? extends Map<? extends K, ? extends V>> executionTimeValue : values) {
                Value<? extends Map<? extends K, ? extends V>> value = executionTimeValue.toProvider().calculateValue(consumer);
                if (value.isMissing()) {
                    return Value.missing();
                } else {
                    entries.putAll(value.getWithoutSideEffect());
                    sideEffectBuilder.add(SideEffect.fixedFrom(value));
                }
            }

            return Value.of(ImmutableMap.copyOf(entries)).withSideEffect(sideEffectBuilder.build());
        }
    }

    private class Configurer {
        private final boolean ignoreAbsent;

        public Configurer(boolean ignoreAbsent) {
            this.ignoreAbsent = ignoreAbsent;
        }

        void addCollector(MapCollector<K, V> collector) {
            addExplicitCollector(collector, ignoreAbsent);
        }

        public void put(K key, V value) {
            Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
            Preconditions.checkNotNull(value, NULL_VALUE_FORBIDDEN_MESSAGE);
            addCollector(new SingleEntry<>(key, value));
        }

        public void put(K key, Provider<? extends V> providerOfValue) {
            Preconditions.checkNotNull(key, NULL_KEY_FORBIDDEN_MESSAGE);
            Preconditions.checkNotNull(providerOfValue, NULL_VALUE_FORBIDDEN_MESSAGE);
            ProviderInternal<? extends V> p = Providers.internal(providerOfValue);
            if (p.getType() != null && !valueType.isAssignableFrom(p.getType())) {
                throw new IllegalArgumentException(String.format("Cannot add an entry to a property of type %s with values of type %s using a provider of type %s.",
                    Map.class.getName(), valueType.getName(), p.getType().getName()));
            }
            addCollector(new EntryWithValueFromProvider<>(key, Providers.internal(providerOfValue)));
        }

        public void putAll(Map<? extends K, ? extends V> entries) {
            addCollector(new EntriesFromMap<>(entries));
        }

        public void putAll(Provider<? extends Map<? extends K, ? extends V>> provider) {
            addCollector(new EntriesFromMapProvider<>(checkMapProvider(provider)));
        }
    }

    /**
     * A fixed value collector, similar to {@link EntriesFromMap} but with a side effect.
     */
    private static class FixedValueCollector<K, V> implements MapCollector<K, V> {
        @Nullable
        private final SideEffect<? super Map<K, V>> sideEffect;
        private final Map<K, V> entries;

        private FixedValueCollector(Map<K, V> entries, @Nullable SideEffect<? super Map<K, V>> sideEffect) {
            this.entries = entries;
            this.sideEffect = sideEffect;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            collector.addAll(entries.entrySet(), dest);
            return sideEffect != null
                ? Value.present().withSideEffect(SideEffect.fixed(entries, sideEffect))
                : Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            collector.addAll(entries.keySet(), dest);
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            visitor.execute(ExecutionTimeValue.fixedValue(entries).withSideEffect(sideEffect));
        }

        @Override
        public MapCollector<K, V> absentIgnoring() {
            // always present
            return this;
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public String toString() {
            return entries.toString();
        }
    }

    private static abstract class AbstractPlusCollector<K, V> implements MapCollector<K, V> {

        protected final MapCollector<K, V> left;
        protected final MapCollector<K, V> right;

        private AbstractPlusCollector(MapCollector<K, V> left, MapCollector<K, V> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public ValueProducer getProducer() {
            return left.getProducer().plus(right.getProducer());
        }

        @Override
        public String toString() {
            return left + " + " + right;
        }
    }

    private static class PlusCollector<K, V> extends AbstractPlusCollector<K, V> {

        public PlusCollector(MapCollector<K, V> left, MapCollector<K, V> right) {
            super(left, right);
        }

        @Override
        public MapCollector<K, V> absentIgnoring() {
            return new AbsentIgnoringPlusCollector<K, V>(left, right);
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return left.calculatePresence(consumer) && right.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<Void> leftValue = left.collectEntries(consumer, collector, dest);
            if (leftValue.isMissing()) {
                return leftValue;
            }
            Value<Void> rightValue = right.collectEntries(consumer, collector, dest);
            if (rightValue.isMissing()) {
                return rightValue;
            }

            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
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
    }

    /**
     * A plus collector that either produces a composition of both of its left and right sides,
     * or Value.present() with empty content (if left or right side are missing).
     */
    private static class AbsentIgnoringPlusCollector<K, V> extends AbstractPlusCollector<K, V> {

        private AbsentIgnoringPlusCollector(MapCollector<K, V> left, MapCollector<K, V> right) {
            super(left, right);
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public MapCollector<K, V> absentIgnoring() {
            return this;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Map<K, V> candidates = new LinkedHashMap<>();
            // we cannot use dest directly because we don't want to emit any entries if either left or right are missing
            Value<Void> leftValue = left.collectEntries(consumer, collector, candidates);
            if (leftValue.isMissing()) {
                return Value.present();
            }
            Value<Void> rightValue = right.collectEntries(consumer, collector, candidates);
            if (rightValue.isMissing()) {
                return Value.present();
            }
            dest.putAll(candidates);
            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            ImmutableSet.Builder<K> candidateKeys = ImmutableSet.builder();
            Value<Void> leftResult = left.collectKeys(consumer, collector, candidateKeys);
            if (leftResult.isMissing()) {
                return Value.present();
            }
            Value<Void> rightResult = right.collectKeys(consumer, collector, candidateKeys);
            if (rightResult.isMissing()) {
                return Value.present();
            }
            dest.addAll(candidateKeys.build());
            return rightResult;
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            boolean[] anyMissing = {false};
            ImmutableList.Builder<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> toVisit = ImmutableList.builder();
            Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> safeVisitor = value -> {
                if (value.isMissing()) {
                    anyMissing[0] = true;
                } else {
                    toVisit.add(value);
                }
            };
            left.calculateExecutionTimeValue(safeVisitor);
            right.calculateExecutionTimeValue(safeVisitor);
            if (!anyMissing[0]) {
                toVisit.build().forEach(it -> visitor.execute(it));
            }
        }
    }
}
