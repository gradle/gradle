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

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyUtil;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildableBackedProvider<B extends Buildable & TaskDependencyContainer, T> extends AbstractProviderWithValue<T> {

    private final B buildable;
    private final Class<T> valueType;
    private final Factory<T> valueFactory;

    public BuildableBackedProvider(B buildable, Class<T> valueType, Factory<T> valueFactory) {
        this.buildable = buildable;
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
        // not a lambda for readability purposes.
        //noinspection Convert2Lambda
        return new ValueProducer() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                buildable.visitDependencies(context);
            }

            @Override
            public void visitProducerTasks(Action<? super Task> visitor) {
                for (Task dependency : buildableDependencies()) {
                    visitor.execute(dependency);
                }
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
        buildable.visitDependencies(new AbstractTaskDependencyResolveContext() {
            @Override
            public void add(Object dependency) {
                hasDependency.set(true);
            }
        });
        return hasDependency.get();
    }

    private Set<? extends Task> buildableDependencies() {
        return TaskDependencyUtil.getDependenciesForInternalUse(buildable);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return Value.ofNullable(valueFactory.create());
    }
}
