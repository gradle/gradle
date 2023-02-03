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

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

public class FlatMapProvider<S, T> extends AbstractMinimalProvider<S> {
    private final ProviderInternal<? extends T> provider;
    private final Transformer<? extends Provider<? extends S>, ? super T> transformer;

    FlatMapProvider(ProviderInternal<? extends T> provider, Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        this.provider = provider;
        this.transformer = transformer;
    }

    @Nullable
    @Override
    public Class<S> getType() {
        return null;
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return backingProvider(consumer).calculatePresence(consumer);
    }

    @Override
    protected Value<? extends S> calculateOwnValue(ValueConsumer consumer) {
        Value<? extends T> value = provider.calculateValue(consumer);
        if (value.isMissing()) {
            return value.asType();
        }
        return doMapValue(value).calculateValue(consumer);
    }

    private ProviderInternal<? extends S> doMapValue(Value<? extends T> value) {
        T unpackedValue = value.getWithoutSideEffect();
        Provider<? extends S> transformedProvider = transformer.transform(unpackedValue);
        if (transformedProvider == null) {
            return Providers.notDefined();
        }

        // Note, that the potential side effect of the transformed provider
        // is going to be executed before this fixed side effect.
        // It is not possible to preserve linear execution order in the general case,
        // as the transformed provider can have side effects hidden under other wrapping providers.
        return Providers.internal(transformedProvider).withSideEffect(SideEffect.fixedFrom(value));
    }

    private ProviderInternal<? extends S> backingProvider(ValueConsumer consumer) {
        Value<? extends T> value = provider.calculateValue(consumer);
        if (value.isMissing()) {
            return Providers.notDefined();
        }
        return doMapValue(value);
    }

    @Override
    public ValueProducer getProducer() {
        return backingProvider(ValueConsumer.IgnoreUnsafeRead).getProducer();
    }

    @Override
    public ExecutionTimeValue<? extends S> calculateExecutionTimeValue() {
        return backingProvider(ValueConsumer.IgnoreUnsafeRead).calculateExecutionTimeValue();
    }

    @Override
    public String toString() {
        return "flatmap(" + provider + ")";
    }
}
