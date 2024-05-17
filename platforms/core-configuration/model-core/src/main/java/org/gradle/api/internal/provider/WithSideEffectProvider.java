/*
 * Copyright 2022 the original author or authors.
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


import javax.annotation.Nullable;

public class WithSideEffectProvider<T> extends AbstractMinimalProvider<T> {

    public static <T> ProviderInternal<T> of(ProviderInternal<T> provider, @Nullable SideEffect<? super T> sideEffect) {
        return sideEffect == null ? provider : new WithSideEffectProvider<>(provider, sideEffect);
    }

    private final ProviderInternal<T> provider;
    private final SideEffect<? super T> sideEffect;

    private WithSideEffectProvider(ProviderInternal<T> provider, SideEffect<? super T> sideEffect) {
        this.provider = provider;
        this.sideEffect = sideEffect;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return provider.getType();
    }

    @Override
    public ValueProducer getProducer() {
        return provider.getProducer();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return provider.calculatePresence(consumer);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return provider.calculateValue(consumer).withSideEffect(sideEffect);
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return provider.calculateExecutionTimeValue().withSideEffect(sideEffect);
    }

    @Override
    public ProviderInternal<T> withSideEffect(@Nullable SideEffect<? super T> sideEffect) {
        if (sideEffect == null) {
            return this;
        }

        return of(provider, SideEffect.composite(this.sideEffect, sideEffect));
    }

    @Override
    public String toString() {
        return "" + provider + " (with side effect " + sideEffect + ")";
    }
}
