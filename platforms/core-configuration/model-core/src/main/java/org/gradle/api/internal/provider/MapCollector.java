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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.internal.Cast;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A supplier of zero or more mappings from value of type {@link K} to value of type {@link V}.
 *
 * <p>
 *     A <code>MapCollector</code> is for {@link DefaultMapProperty} and {@link MapSupplier} what {@link Collector} is for {@link AbstractCollectionProperty} and {@link CollectionSupplier}.
 * </p>
 */
public interface MapCollector<K, V> extends ValueSupplier {

    Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest);

    Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest);

    void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor);

    /**
     * Returns a collector that never returns a missing value.
     */
    default MapCollector<K, V> absentIgnoring() {
        MapCollector<K, V> delegate = this;
        return new MapCollector<K, V>() {
            @Override
            public Value<Void> collectEntries(ValueConsumer consumer, MapEntryCollector<K, V> collector, Map<K, V> dest) {
                Map<K, V> batch = new LinkedHashMap<>();
                Value<Void> result = delegate.collectEntries(consumer, collector, batch);
                if (result.isMissing()) {
                    return Value.present();
                }
                dest.putAll(batch);
                return result;
            }

            @Override
            public Value<Void> collectKeys(ValueConsumer consumer, ValueCollector<K> collector, ImmutableCollection.Builder<K> dest) {
                ImmutableCollection.Builder<K> batch = ImmutableSet.builder();
                Value<Void> result = delegate.collectKeys(consumer, collector, batch);
                if (result.isMissing()) {
                    return Value.present();
                }
                dest.addAll(batch.build());
                return result;
            }

            @Override
            public void calculateExecutionTimeValue(Action<ExecutionTimeValue<? extends Map<? extends K, ? extends V>>> visitor) {
                List<ExecutionTimeValue> values = new LinkedList<>();
                delegate.calculateExecutionTimeValue(it -> collectIfPresent(values, it));
                values.forEach(it -> visitor.execute(Cast.uncheckedNonnullCast(it)));
            }

            private void collectIfPresent(List<ExecutionTimeValue> values, ExecutionTimeValue<? extends Map<? extends K, ? extends V>> it) {
                if (!it.isMissing()) {
                    values.add(it);
                }
            }

            @Override
            public ValueProducer getProducer() {
                return delegate.getProducer();
            }

            @Override
            public boolean calculatePresence(ValueConsumer consumer) {
                // always present
                return true;
            }

            @Override
            public MapCollector<K, V> absentIgnoring() {
                return this;
            }
        };
    }
}
