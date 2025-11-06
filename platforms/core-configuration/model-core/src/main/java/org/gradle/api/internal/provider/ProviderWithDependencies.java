/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.internal.Factory;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProviderWithDependencies<T> extends AbstractProviderWithValue<T> {

    private final TaskDependencyContainer dependencies;
    private final Class<T> valueType;
    private final Factory<T> valueFactory;

    public ProviderWithDependencies(TaskDependencyContainer dependencies, Class<T> valueType, Factory<T> valueFactory) {
        this.dependencies = dependencies;
        this.valueType = valueType;
        this.valueFactory = valueFactory;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return valueType;
    }

    @Override
    public ValueProducer getProducer() {
        return new ValueProducer() {
            @Override
            public TaskDependencyContainer getDependencies() {
                return dependencies;
            }

            @Override
            public TaskDependencyContainer getContentDependencies() {
                return dependencies;
            }
        };
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        if (hasDependencies()) {
            return ExecutionTimeValue.changingValue(this);
        }
        return ExecutionTimeValue.fixedValue(get());
    }

    private boolean hasDependencies() {
        AtomicBoolean hasDependency = new AtomicBoolean(false);
        dependencies.visitDependencies(new AbstractTaskDependencyResolveContext() {
            @Override
            public void add(Object dependency) {
                hasDependency.set(true);
            }
        });
        return hasDependency.get();
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        T value = valueFactory.create();
        if (value == null) {
            // AbstractProviderWithValue expects the factory to always return a non-null value
            throw new NullPointerException("Value factory must not return null");
        }
        return Value.of(value);
    }
}
