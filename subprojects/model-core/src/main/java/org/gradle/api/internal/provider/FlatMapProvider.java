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
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

class FlatMapProvider<S, T> extends AbstractMinimalProvider<S> {
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
    public boolean isPresent() {
        T value = provider.getOrNull();
        if (value == null) {
            return false;
        }
        return map(value).isPresent();
    }

    @Override
    protected Value<? extends S> calculateOwnValue() {
        Value<? extends T> value = provider.calculateValue();
        if (value.isMissing()) {
            return value.asType();
        }
        return map(value.get()).calculateValue();
    }

    private ProviderInternal<? extends S> map(T value) {
        Provider<? extends S> result = transformer.transform(value);
        if (result == null) {
            return Providers.notDefined();
        }
        return Providers.internal(result);
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return Providers.internal(map(provider.get())).maybeVisitBuildDependencies(context);
    }

    @Override
    public String toString() {
        return "flatmap(" + provider + ")";
    }
}
