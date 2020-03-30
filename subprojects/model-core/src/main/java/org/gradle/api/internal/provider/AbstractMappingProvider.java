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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;

public abstract class AbstractMappingProvider<OUT, IN> extends AbstractMinimalProvider<OUT> {
    private final Class<OUT> type;
    private final ProviderInternal<? extends IN> provider;

    public AbstractMappingProvider(Class<OUT> type, ProviderInternal<? extends IN> provider) {
        this.type = type;
        this.provider = provider;
    }

    @Nullable
    @Override
    public Class<OUT> getType() {
        return type;
    }

    @Override
    public boolean isValueProducedByTask() {
        return provider.isValueProducedByTask();
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
        provider.visitProducerTasks(visitor);
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return provider.maybeVisitBuildDependencies(context);
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

    @Override
    protected Value<OUT> calculateOwnValue() {
        Value<? extends IN> value = provider.calculateValue();
        if (value.isMissing()) {
            return value.asType();
        }
        return Value.of(mapValue(value.get()));
    }

    protected abstract OUT mapValue(IN v);

    // Included in toString() output
    protected abstract String getMapDescription();

    @Override
    public String toString() {
        return getMapDescription() + "(" + provider + ")";
    }
}
