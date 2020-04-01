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
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

class OrElseFixedValueProvider<T> extends AbstractProviderWithValue<T> {
    private final ProviderInternal<? extends T> provider;
    private final T fallbackValue;

    public OrElseFixedValueProvider(ProviderInternal<? extends T> provider, T fallbackValue) {
        this.provider = provider;
        this.fallbackValue = fallbackValue;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return Cast.uncheckedCast(provider.getType());
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
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends T> value = provider.calculateExecutionTimeValue();
        if (value.isMissing()) {
            // Use fallback value
            return ExecutionTimeValue.fixedValue(fallbackValue);
        } else if (value.isFixedValue()) {
            // Result is fixed value, use it
            return value;
        } else {
            // Value is changing, so keep the logic
            return ExecutionTimeValue.changingValue(new OrElseFixedValueProvider<>(value.getChangingValue(), fallbackValue));
        }
    }

    @Override
    public T get() {
        T value = provider.getOrNull();
        return value != null ? value : fallbackValue;
    }
}
