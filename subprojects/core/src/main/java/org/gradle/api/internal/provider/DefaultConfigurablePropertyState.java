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

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.ConfigurablePropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;

import java.util.Set;

public class DefaultConfigurablePropertyState<T> implements ConfigurablePropertyState<T> {

    static final String NON_NULL_VALUE_EXCEPTION_MESSAGE = "Needs to set a non-null value before it can be retrieved";
    private final DefaultTaskDependency buildDependency;
    private T value;

    public DefaultConfigurablePropertyState(TaskResolver taskResolver) {
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    @Override
    public void set(T value) {
        this.value = value;
    }

    @Override
    public void set(Provider<? extends T> provider) {
        this.value = provider.getOrNull();
    }

    @Internal
    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException(NON_NULL_VALUE_EXCEPTION_MESSAGE);
        }

        return value;
    }

    @Internal
    @Override
    public T getOrNull() {
        return value;
    }

    @Override
    public boolean isPresent() {
        return value != null;
    }

    @Internal
    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependency;
    }

    @Override
    public Set<Object> getBuiltBy() {
        return buildDependency.getValues();
    }

    @Override
    public ConfigurablePropertyState setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    public ConfigurablePropertyState<T> builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    @Override
    public String toString() {
        return String.format("value: %s", value);
    }
}