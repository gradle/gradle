/*
 * Copyright 2017 the original author or authors.
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

import static org.gradle.api.internal.provider.Providers.NULL_VALUE;

public abstract class AbstractProvider<T> implements ProviderInternal<T> {

    @Override
    public T get() {
        T evaluatedValue = getOrNull();

        if (evaluatedValue == null) {
            throw new IllegalStateException(NULL_VALUE);
        }

        return evaluatedValue;
    }

    @Override
    public T getOrElse(T defaultValue) {
        T value = getOrNull();
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<S, T>(transformer, this);
    }

    @Override
    public boolean isPresent() {
        return getOrNull() != null;
    }

    @Override
    public String toString() {
        return String.format("value: %s", getOrNull());
    }

}
