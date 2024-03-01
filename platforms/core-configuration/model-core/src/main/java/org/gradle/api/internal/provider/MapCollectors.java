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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.internal.Cast;

import javax.annotation.Nonnull;
import java.util.Map;

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
        public MapCollector<K, V> absentIgnoring() {
            // always present
            return this;
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

        @Override
        public String toString() {
            return String.format("{%s=%s}", key, value);
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
        public MapCollector<K, V> absentIgnoring() {
            return new EntriesFromMapProvider<>(providerOfValue.map(value -> ImmutableMap.of(key, value)), true);
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

        @Override
        public String toString() {
            return String.format("entry{%s=%s}", key, providerOfValue);
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
        public MapCollector<K, V> absentIgnoring() {
            // always present
            return this;
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

        @Override
        public String toString() {
            return entries.toString();
        }
    }

    public static class EntriesFromMapProvider<K, V> implements MapCollector<K, V> {

        private final ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries;
        private final boolean ignoreAbsent;

        public EntriesFromMapProvider(ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries) {
            this(providerOfEntries, false);
        }

        private EntriesFromMapProvider(ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries, boolean ignoreAbsent) {
            this.providerOfEntries = ignoreAbsent ? neverMissing(Cast.uncheckedNonnullCast(providerOfEntries)) : providerOfEntries;
            this.ignoreAbsent = ignoreAbsent;
        }

        @Override
        public MapCollector<K, V> absentIgnoring() {
            return ignoreAbsent ? this : new EntriesFromMapProvider<>(providerOfEntries, true);
        }

        @Nonnull
        private static <K, V> ProviderInternal<? extends Map<? extends K, ? extends V>> neverMissing(ProviderInternal<Map<? extends K, ? extends V>> provider) {
            return Cast.uncheckedNonnullCast(provider.orElse(ImmutableMap.of()));
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return ignoreAbsent || providerOfEntries.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<? extends Map<? extends K, ? extends V>> value = providerOfEntries.calculateValue(consumer);
            if (value.isMissing()) {
                return ignoreAbsent ? Value.present() : value.asType();
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

        @Override
        public String toString() {
            return String.valueOf(providerOfEntries);
        }
    }
}
