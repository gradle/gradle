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
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.util.List;
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
        public boolean isPresent() {
            return true;
        }

        @Override
        public Value<Void> collectEntries(MapEntryCollector<K, V> collector, Map<K, V> dest) {
            collector.add(key, value, dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            collector.add(key, dest);
            return Value.present();
        }

        @Override
        public void visit(List<ProviderInternal<? extends Map<? extends K, ? extends V>>> sources) {
            sources.add(Providers.of(ImmutableMap.of(key, value)));
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return false;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }

        @Override
        public boolean isValueProducedByTask() {
            return false;
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
        public boolean isPresent() {
            return providerOfValue.isPresent();
        }

        @Override
        public Value<Void> collectEntries(MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<? extends V> value = providerOfValue.calculateValue();
            if (value.isMissing()) {
                return value.asType();
            }
            collector.add(key, value.get(), dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            if (providerOfValue.isPresent()) {
                collector.add(key, dest);
                return Value.present();
            } else {
                return Value.missing();
            }
        }

        @Override
        public void visit(List<ProviderInternal<? extends Map<? extends K, ? extends V>>> sources) {
            sources.add(providerOfValue.map(v -> ImmutableMap.of(key, v)));
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return providerOfValue.maybeVisitBuildDependencies(context);
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            providerOfValue.visitProducerTasks(visitor);
        }

        @Override
        public boolean isValueProducedByTask() {
            return providerOfValue.isValueProducedByTask();
        }
    }

    public static class EntriesFromMap<K, V> implements MapCollector<K, V> {

        private final Map<? extends K, ? extends V> entries;

        public EntriesFromMap(Map<? extends K, ? extends V> entries) {
            this.entries = entries;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public Value<Void> collectEntries(MapEntryCollector<K, V> collector, Map<K, V> dest) {
            collector.addAll(entries.entrySet(), dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            collector.addAll(entries.keySet(), dest);
            return Value.present();
        }

        @Override
        public void visit(List<ProviderInternal<? extends Map<? extends K, ? extends V>>> sources) {
            sources.add(Providers.of(entries));
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return false;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }

        @Override
        public boolean isValueProducedByTask() {
            return false;
        }
    }

    public static class EntriesFromMapProvider<K, V> implements MapCollector<K, V> {

        private final ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries;

        public EntriesFromMapProvider(ProviderInternal<? extends Map<? extends K, ? extends V>> providerOfEntries) {
            this.providerOfEntries = providerOfEntries;
        }

        @Override
        public boolean isPresent() {
            return providerOfEntries.isPresent();
        }

        @Override
        public Value<Void> collectEntries(MapEntryCollector<K, V> collector, Map<K, V> dest) {
            Value<? extends Map<? extends K, ? extends V>> value = providerOfEntries.calculateValue();
            if (value.isMissing()) {
                return value.asType();
            }
            collector.addAll(value.get().entrySet(), dest);
            return Value.present();
        }

        @Override
        public Value<Void> collectKeys(ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
            Map<? extends K, ? extends V> entries = providerOfEntries.getOrNull();
            if (entries != null) {
                collector.addAll(entries.keySet(), dest);
                return Value.present();
            } else {
                return Value.missing();
            }
        }

        @Override
        public void visit(List<ProviderInternal<? extends Map<? extends K, ? extends V>>> sources) {
            sources.add(providerOfEntries);
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return providerOfEntries.maybeVisitBuildDependencies(context);
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            providerOfEntries.visitProducerTasks(visitor);
        }

        @Override
        public boolean isValueProducedByTask() {
            return providerOfEntries.isValueProducedByTask();
        }
    }
}
