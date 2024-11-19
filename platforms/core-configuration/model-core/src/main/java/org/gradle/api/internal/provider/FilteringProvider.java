/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A filtering provider that uses a spec to filter the value of another provider.
 **/
public class FilteringProvider<T> extends AbstractMinimalProvider<T> {

    protected final ProviderInternal<T> provider;
    protected final Spec<? super T> spec;

    public FilteringProvider(
        ProviderInternal<T> provider,
        Spec<? super T> spec
    ) {
        this.spec = spec;
        this.provider = provider;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return provider.getType();
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.getProducer();
        }
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            ExecutionTimeValue<? extends T> value = provider.calculateExecutionTimeValue();
            if (value.isMissing()) {
                return value;
            }
            if (value.hasChangingContent()) {
                // Need the value contents in order to transform it to produce the value of this provider,
                // so if the value or its contents are built by tasks, the value of this provider is also built by tasks
                return ExecutionTimeValue.changingValue(new FilteringProvider<>(value.toProvider(), spec));
            }

            return ExecutionTimeValue.value(filterValue(context, value.toValue()));
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context);
            Value<? extends T> value = provider.calculateValue(consumer);
            return filterValue(context, value);
        }
    }

    @Nonnull
    protected Value<? extends T> filterValue(@SuppressWarnings("unused") EvaluationScopeContext context, Value<? extends T> value) {
        if (value.isMissing()) {
            return value.asType();
        }
        T unpackedValue = value.getWithoutSideEffect();
        if (spec.isSatisfiedBy(unpackedValue)) {
            return value;
        } else {
            return Value.missing();
        }
    }

    protected void beforeRead(@SuppressWarnings("unused") EvaluationScopeContext context) {
        provider.getProducer().visitContentProducerTasks(producer -> {
            if (!producer.getState().getExecuted()) {
                throw new InvalidUserCodeException(
                    String.format("Querying the filtered value of %s before %s has completed is not supported", provider, producer)
                );
            }
        });
    }

    @Override
    protected String toStringNoReentrance() {
        return "filter(" + (getType() == null ? "" : getType().getName() + " ") + provider + ")";
    }
}
