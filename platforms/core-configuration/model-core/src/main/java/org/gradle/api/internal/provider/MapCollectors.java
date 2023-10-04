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

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.internal.lambdas.SerializableLambdas;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public class MapCollectors {

    public static class SingleEntry<K, V> implements MapCollector<K, V> {

        private final K key;
        private final V value;

        public SingleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            collector.add(key, value, dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            collector.add(key, dest);
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            visitor.execute(ExecutionTimeValue.fixedValue(ImmutableMap.of(key, value)));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SingleEntry<?, ?> that = (SingleEntry<?, ?>) o;
            return Objects.equal(key, that.key) && Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key, value);
        }
    }

    public static class EntryWithValueFromProvider<K, V> implements MapCollector<K, V> {
        private final K key;
        private final ProviderInternal<? extends V> providerOfValue;

        public EntryWithValueFromProvider(K key, ProviderInternal<? extends V> providerOfValue) {
            this.key = key;
            this.providerOfValue = providerOfValue;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return providerOfValue.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<? extends V> value = providerOfValue.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }
            collector.add(key, value.getWithoutSideEffect(), dest);
            return Value.present().withSideEffect(SideEffect.fixedFrom(value));
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            if (providerOfValue.calculatePresence(consumer)) {
                collector.add(key, dest);
                return Value.present();
            } else {
                return Value.missing();
            }
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            ExecutionTimeValue<? extends V> value = providerOfValue.calculateExecutionTimeValue();
            if (value.isMissing()) {
                visitor.execute(ExecutionTimeValue.missing());
            } else if (value.hasFixedValue()) {
                // transform preserving side effects
                visitor.execute(ExecutionTimeValue.value(value.toValue().transform(v -> ImmutableMap.of(key, v))));
            } else {
                visitor.execute(ExecutionTimeValue.changingValue(
                    value.getChangingValue().map(SerializableLambdas.transformer(v -> ImmutableMap.of(key, v)))));
            }
        }

        @Override
        public ValueProducer getProducer() {
            return providerOfValue.getProducer();
        }
    }

    public static class EntriesFromMap<K, V> implements MapCollector<K, V> {

        private final Map<? extends K, ? extends V> entries;

        public EntriesFromMap(Map<? extends K, ? extends V> entries) {
            this.entries = entries;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            collector.addAll(entries.entrySet(), dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            collector.addAll(entries.keySet(), dest);
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> sources) {
            sources.execute(ExecutionTimeValue.fixedValue(entries));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    public static class EntriesFromMapProvider<K, V> implements MapCollector<K, V> {

        private final ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries;

        public EntriesFromMapProvider(ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries) {
            this.providerOfEntries = providerOfEntries;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return providerOfEntries.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<? extends Map<? extends K, ? extends V>> value = providerOfEntries.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }
            collector.addAll(value.getWithoutSideEffect().entrySet(), dest);
            return Value.present().withSideEffect(SideEffect.fixedFrom(value));
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            Value<? extends Map<? extends K, ? extends V>> value = providerOfEntries.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }
            collector.addAll(value.getWithoutSideEffect().keySet(), dest);
            return Value.present().withSideEffect(SideEffect.fixedFrom(value));
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            visitor.execute(providerOfEntries.calculateExecutionTimeValue());
        }

        @Override
        public ValueProducer getProducer() {
            return providerOfEntries.getProducer();
        }
    }

    public static class FilteringCollector<K, V> implements MapCollector<K, V> {
        private final MapCollector<K, V> upstream;
        private final Predicate<K> keyFilter;
        private final Predicate<V> valueFilter;

        public FilteringCollector(MapCollector<K, V> collector, Predicate<K> keyFilter, Predicate<V> valueFilter) {
            this.upstream = collector;
            this.keyFilter = keyFilter;
            this.valueFilter = valueFilter;
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            ImmutableSet.Builder<K> baseBuilder = ImmutableSet.builder();
            Value<Void> baseValue = upstream.collectKeys(consumer, collector, baseBuilder);
            if (baseValue.isMissing()) {
                return baseValue;
            }
            ImmutableCollection<K> baseElements = baseBuilder.build();
            dest.addAll(Iterables.filter(baseElements, keyFilter::test));
            return baseValue;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Map<K, V> baseEntries = new LinkedHashMap<>();
            Value<Void> baseValue = upstream.collectEntries(consumer, collector, baseEntries);
            if (baseValue.isMissing()) {
                return baseValue;
            }
            Iterables.filter(baseEntries.entrySet(), e -> keyFilter.test(e.getKey()) && valueFilter.test(e.getValue()))
                .forEach(e -> dest.put(e.getKey(), e.getValue()));
            return baseValue;
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            // TODO-RC should only invoke visitors for entries that match the filter?
            upstream.calculateExecutionTimeValue(visitor);
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return upstream.calculatePresence(consumer);
        }

        @Override
        public ValueProducer getProducer() {
            return upstream.getProducer();
        }
    }

    public static class PlusCollector<K, V> implements MapCollector<K, V> {
        private final MapCollector<K, V> left;
        private final MapCollector<K, V> right;
        private final boolean pruning;

        public PlusCollector(MapCollector<K, V> left, MapCollector<K, V> right, boolean pruning) {
            this.left = left;
            this.right = right;
            this.pruning = pruning;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return pruning ?
                left.calculatePresence(consumer) || right.calculatePresence(consumer) :
                left.calculatePresence(consumer) && right.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<Void> leftValue = left.collectEntries(consumer, collector, dest);
            if (leftValue.isMissing() && !pruning) {
                return leftValue;
            }
            Value<Void> rightValue = right.collectEntries(consumer, collector, dest);
            if (rightValue.isMissing() && (!pruning || leftValue.isMissing())) {
                return rightValue;
            }

            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            Value<Void> leftResult = left.collectKeys(consumer, collector, dest);
            if (leftResult.isMissing() && !pruning) {
                return leftResult;
            }
            Value<Void> rightResult = right.collectKeys(consumer, collector, dest);
            return rightResult.isMissing() ? leftResult : rightResult;
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

    public static class MinusCollector<K, V> implements MapCollector<K, V> {
        private final MapCollector<K, V> upstream;
        private final Collector<K> exclusions;
        private final boolean pruning;

        public MinusCollector(MapCollector<K, V> upstream, Collector<K> exclusions, boolean pruning) {
            this.upstream = upstream;
            this.exclusions = exclusions;
            this.pruning = pruning;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return pruning ?
                upstream.calculatePresence(consumer) || exclusions.calculatePresence(consumer) :
                upstream.calculatePresence(consumer) && exclusions.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Map<K, V> upstreamEntries = new LinkedHashMap<>();
            Value<Void> upstreamValue = upstream.collectEntries(consumer, collector, upstreamEntries);
            if (upstreamValue.isMissing()) {
                return upstreamValue;
            }
            ImmutableSet.Builder<K> keysToExcludeBuilder = ImmutableSet.builder();
            Value<Void> exclusionValue = exclusions.collectEntries(consumer, collector.asKeyCollector(), keysToExcludeBuilder);
            if (exclusionValue.isMissing() && (!pruning || upstreamValue.isMissing())) {
                return upstreamValue;
            }
            ImmutableSet<K> keysToExclude = keysToExcludeBuilder.build();
            Map<K, V> filtered = Maps.filterKeys(upstreamEntries, Predicates.not(keysToExclude::contains));
            dest.putAll(filtered);
            //TODO-RC what to do about side effects?
            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(upstreamValue))
                .withSideEffect(SideEffect.fixedFrom(exclusionValue));
        }

        @Override
        public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            Value<Void> leftResult = upstream.collectKeys(consumer, collector, dest);
            if (leftResult.isMissing() && !pruning) {
                return leftResult;
            }
            //TODO-RC finish this properly
            Value<Void> rightResult = exclusions.collectEntries(consumer, collector, dest);
            return rightResult.isMissing() ? leftResult : rightResult;
        }

        @Override
        public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
            upstream.calculateExecutionTimeValue(visitor);
            //TODO-RC what do about E-T values?
        }

        @Override
        public ValueProducer getProducer() {
            return upstream.getProducer().plus(exclusions.getProducer());
        }
    }
}
