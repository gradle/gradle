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

import org.gradle.internal.Cast;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nullable;

class OrElseFixedValueProvider<T> extends AbstractProviderWithValue<T> {
    private final ProviderInternal<? extends T> provider;
    private final T fallbackValue;

    public OrElseFixedValueProvider(ProviderInternal<? extends T> provider, T fallbackValue) {
        this.provider = provider;
        this.fallbackValue = fallbackValue;
    }

    @Override
    protected String toStringNoReentrance() {
        return String.format("or(%s, fixed(%s))", provider, fallbackValue);
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return Cast.uncheckedCast(provider.getType());
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext context = openScope()) {
            return new OrElseValueProducer(context, provider);
        }
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext ignored = openScope()) {
            ExecutionTimeValue<? extends T> value = provider.calculateExecutionTimeValue();
            if (value.isMissing()) {
                // Use fallback value
                return ExecutionTimeValue.fixedValue(fallbackValue);
            } else if (value.hasFixedValue()) {
                // Result is fixed value, use it
                return value;
            } else {
                // Value is changing, so keep the logic
                return ExecutionTimeValue.changingValue(new OrElseFixedValueProvider<>(value.getChangingValue(), fallbackValue));
            }
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            Value<? extends T> value = provider.calculateValue(consumer);
            if (value.isMissing()) {
                return Value.of(fallbackValue);
            } else {
                return value;
            }
        }
    }
}
