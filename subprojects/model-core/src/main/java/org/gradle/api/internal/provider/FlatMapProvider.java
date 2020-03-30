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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
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
    public boolean isPresent() {
        return backingProvider().isPresent();
    }

    @Override
    protected Value<? extends S> calculateOwnValue() {
        Value<? extends T> value = provider.calculateValue();
        if (value.isMissing()) {
            return value.asType();
        }
        return doMapValue(value.get()).calculateValue();
    }

    private ProviderInternal<? extends S> doMapValue(T value) {
        Provider<? extends S> result = transformer.transform(value);
        if (result == null) {
            return Providers.notDefined();
        }
        return Providers.internal(result);
    }

    public ProviderInternal<? extends S> backingProvider() {
        Value<? extends T> value = provider.calculateValue();
        if (value.isMissing()) {
            return Providers.notDefined();
        }
        return doMapValue(value.get());
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
        backingProvider().visitProducerTasks(visitor);
    }

    @Override
    public boolean isValueProducedByTask() {
        // Need the content in order to transform it to produce the value of this provider, so if the content is built by tasks, the value is also built by tasks
        return backingProvider().isValueProducedByTask() || !getProducerTasks().isEmpty();
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return backingProvider().maybeVisitBuildDependencies(context);
    }

    @Override
    public String toString() {
        return "flatmap(" + provider + ")";
    }
}
