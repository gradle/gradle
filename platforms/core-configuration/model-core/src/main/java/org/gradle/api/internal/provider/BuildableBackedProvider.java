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
import org.gradle.api.internal.tasks.TaskDependencyUtil;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.Set;

public class BuildableBackedProvider<T> extends AbstractProviderWithValue<T> {

    private final Buildable buildable;
    private final Class<T> valueType;
    private final Factory<T> valueFactory;

    public BuildableBackedProvider(Buildable buildable, Class<T> valueType, Factory<T> valueFactory) {
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
        return new ValueProducer() {
            @Override
            public boolean isProducesDifferentValueOverTime() {
                return false;
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
        if (contentsAreBuiltByTask()) {
            return ExecutionTimeValue.changingValue(this);
        }
        return ExecutionTimeValue.fixedValue(get());
    }

    private boolean contentsAreBuiltByTask() {
        return !buildableDependencies().isEmpty();
    }

    private Set<? extends Task> buildableDependencies() {
        return TaskDependencyUtil.getDependenciesForInternalUse(buildable);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return Value.ofNullable(valueFactory.create());
    }
}
