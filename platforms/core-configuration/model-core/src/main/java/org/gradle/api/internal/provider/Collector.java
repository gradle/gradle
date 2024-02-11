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
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.internal.Cast;

import java.util.LinkedList;
import java.util.List;

/**
 * A collector is a value supplier of zero or more values of type {@link T}.
 * <p>
 *     A <code>Collector</code> represents an increment to a collection property.
 * </p>
 */
public interface Collector<T> extends ValueSupplier {
    Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest);

    int size();

    void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor);

    /**
     * Returns a collector that never returns a missing value.
     */
    default Collector<T> absentIgnoring() {
        Collector<T> delegate = this;
        return new Collector<T>() {
            @Override
            public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
                ImmutableCollection.Builder<T> batch = ImmutableList.builder();
                Value<Void> result = delegate.collectEntries(consumer, collector, batch);
                if (result.isMissing()) {
                    return Value.present();
                }
                dest.addAll(batch.build());
                return result;
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
                List<ExecutionTimeValue> values = new LinkedList<>();
                delegate.calculateExecutionTimeValue(it -> collectIfPresent(values, it));
                values.forEach(it -> visitor.execute(Cast.uncheckedNonnullCast(it)));
            }

            private void collectIfPresent(List<ExecutionTimeValue> values, ExecutionTimeValue<? extends Iterable<? extends T>> it) {
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
        };
    }
}
