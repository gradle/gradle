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

package org.gradle.api.internal

import org.gradle.api.Named
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSanitizer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.internal.DisplayName

import java.util.function.BiFunction

class TestNamedProvider<T> implements ProviderInternal<T>, Named {

    private final String name
    private final T t

    TestNamedProvider(String name, T t) {
        this.name = name
        this.t = t
    }

    @Override
    String getName() {
        return name
    }

    @Override
    T get() {
        return T
    }

    @Override
    T getOrNull() {
        return t
    }

    @Override
    T getOrElse(T defaultValue) {
        return t == null ? t : defaultValue
    }

    @Override
    boolean isPresent() {
        return t != null
    }

    @Override
    Provider<T> orElse(T t) {
        throw new UnsupportedOperationException()
    }

    @Override
    Provider<T> orElse(Provider<? extends T> provider) {
        throw new UnsupportedOperationException()
    }

    @Override
    Provider<T> forUseAtConfigurationTime() {
        throw new UnsupportedOperationException()
    }

    @Override
    <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        throw new UnsupportedOperationException()
    }

    @Override
    ValueProducer getProducer() {
        throw new UnsupportedOperationException()
    }

    @Override
    boolean calculatePresence(ValueConsumer consumer) {
        throw new UnsupportedOperationException()
    }

    @Override
    void visitDependencies(TaskDependencyResolveContext context) {
        throw new UnsupportedOperationException()
    }

    @Override
    Class<T> getType() {
        return t.class
    }

    @Override
    <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer) {
        throw new UnsupportedOperationException()
    }

    @Override
    Value<? extends T> calculateValue(ValueConsumer consumer) {
        Value.ofNullable(t)
    }

    @Override
    ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
        throw new UnsupportedOperationException()
    }

    @Override
    ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
        throw new UnsupportedOperationException()
    }

    @Override
    ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        throw new UnsupportedOperationException()
    }

    @Override
    Provider<T> filter(Spec<? super T> spec) {
        throw new UnsupportedOperationException()
    }

    @Override
    <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super T> transformer) {
        throw new UnsupportedOperationException()
    }
}
