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
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class BuildableBackedSetProvider<T extends Buildable, V> extends AbstractProviderWithValue<Set<V>> {

    private final T buildable;
    private final Factory<Set<V>> valueFactory;

    public BuildableBackedSetProvider(T buildable, Factory<Set<V>> valueFactory) {
        this.buildable = buildable;
        this.valueFactory = valueFactory;
    }

    @Nullable
    @Override
    public Class<Set<V>> getType() {
        return Cast.uncheckedCast(Set.class);
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
                for (Task dependency : buildable.getBuildDependencies().getDependencies(null)) {
                    visitor.execute(dependency);
                }
            }
        };
    }

    @Override
    public ExecutionTimeValue<? extends Set<V>> calculateExecutionTimeValue() {
        if (contentsAreBuiltByTask()) {
            return ExecutionTimeValue.changingValue(this);
        }
        return ExecutionTimeValue.fixedValue(get());
    }

    private boolean contentsAreBuiltByTask() {
        return !buildable.getBuildDependencies().getDependencies(null).isEmpty();
    }

    @Override
    protected Value<? extends Set<V>> calculateOwnValue(ValueConsumer consumer) {
        return Value.ofNullable(valueFactory.create());
    }
}
