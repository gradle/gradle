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
package org.gradle.api.internal.provider;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A provider that takes a list of providers of values and provides a list of those values.
 *
 * This effectively converts a {@code List<Provider<T>>} to a {@code Provider<List<T>>}.
 *
 * @param <R> The type of the values that all source providers must share.
 */
public class MergeProvider<R> extends AbstractMinimalProvider<List<R>> {

    private final List<Provider<R>> items;

    public MergeProvider(List<Provider<R>> items) {
        this.items = ImmutableList.copyOf(items);
    }

    @Override
    protected String toStringNoReentrance() {
        return String.format("merge([%s])", Joiner.on(", ").join(items));
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        for (Provider<R> provider : items) {
            if (!Providers.internal(provider).calculatePresence(consumer)) {
                return false;
            }
        }

        // Purposefully only calculate full value if all items are present, to save time
        return super.calculatePresence(consumer);
    }

    @Override
    public ExecutionTimeValue<? extends List<R>> calculateExecutionTimeValue() {
        for (Provider<R> provider : items) {
            if (isChangingValue(Providers.internal(provider))) {
                return ExecutionTimeValue.changingValue(this);
            }
        }

        return super.calculateExecutionTimeValue();
    }

    private boolean isChangingValue(ProviderInternal<?> provider) {
        return provider.calculateExecutionTimeValue().isChangingValue();
    }

    @Override
    protected Value<List<R>> calculateOwnValue(ValueConsumer consumer) {

        List<Value<? extends R>> values = new ArrayList<>(items.size());
        for (Provider<R> provider : items) {
            Value<? extends R> value = Providers.internal(provider).calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }
            values.add(value);
        }

        ImmutableList.Builder<R> result = ImmutableList.builderWithExpectedSize(values.size());
        for (Value<? extends R> value : values) {
            result.add(value.getWithoutSideEffect());
        }

        Value<List<R>> finalValue = Value.ofNullable(result.build());
        for (Value<? extends R> value : values) {
            finalValue = finalValue.withSideEffect(SideEffect.fixedFrom(value));
        }

        return finalValue;
    }

    @Nullable
    @Override
    public Class<List<R>> getType() {
        return Cast.uncheckedCast(List.class);
    }

    @Override
    public ValueProducer getProducer() {
        ImmutableList.Builder<ValueProducer> producers = ImmutableList.builderWithExpectedSize(items.size());
        for (Provider<R> item : items) {
            producers.add(Providers.internal(item).getProducer());
        }
        return new MergeValueProducer(producers.build());
    }

    private static class MergeValueProducer implements ValueProducer {

        private final List<ValueProducer> items;

        public MergeValueProducer(List<ValueProducer> items) {
            this.items = items;
        }

        @Override
        public boolean isKnown() {
            for (ValueProducer item : items) {
                if (item.isKnown()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            for (ValueProducer item : items) {
                item.visitProducerTasks(visitor);
            }
        }
    }
}
