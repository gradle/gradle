/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.provider.Provider;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.state.Managed;
import org.jspecify.annotations.Nullable;

/**
 * A provider whose value comes from a delegate provider, but whose producer also includes
 * the producer of another provider that the delegate was derived from.
 * <p>
 * Used when the delegate cannot track the producer of its inputs itself,
 * for example a provider backed by a {@link org.gradle.api.provider.ValueSource}.
 */
public class WithProducerProvider<T> extends AbstractMinimalProvider<T> {

    public static <T> ProviderInternal<T> of(Provider<T> provider, Provider<?> producerSource) {
        return new WithProducerProvider<>(Providers.internal(provider), Providers.internal(producerSource));
    }

    private final ProviderInternal<T> provider;
    private final ProviderInternal<?> producerSource;

    private WithProducerProvider(ProviderInternal<T> provider, ProviderInternal<?> producerSource) {
        this.provider = provider;
        this.producerSource = producerSource;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return provider.getType();
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.getProducer().plus(producerSource.getProducer());
        }
    }

    @Override
    public boolean isImmutable() {
        return provider instanceof Managed && ((Managed) provider).isImmutable();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.calculatePresence(consumer);
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.calculateValue(consumer);
        }
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext ignored = openScope()) {
            return provider.calculateExecutionTimeValue();
        }
    }

    @Override
    protected String toStringNoReentrance() {
        return provider + " (with producer of " + producerSource + ")";
    }
}
