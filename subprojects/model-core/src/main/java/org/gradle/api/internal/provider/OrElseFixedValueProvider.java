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

import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;

class OrElseFixedValueProvider<T> extends AbstractProviderWithValue<T> {
    private final ProviderInternal<T> provider;
    private final T value;

    public OrElseFixedValueProvider(ProviderInternal<T> provider, T value) {
        this.provider = provider;
        this.value = value;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return provider.getType();
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        if (provider.isValueProducedByTask() || provider.isPresent()) {
            // either the provider value will be used, or we don't know yet
            return provider.maybeVisitBuildDependencies(context);
        } else {
            // provider value will not be used, so there are no dependencies
            return true;
        }
    }

    @Override
    public boolean isValueProducedByTask() {
        return provider.isValueProducedByTask();
    }

    @Override
    public T get() {
        T value = provider.getOrNull();
        return value != null ? value : this.value;
    }
}
