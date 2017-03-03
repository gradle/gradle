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
import org.gradle.api.provider.ConfigurableProvider;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;

import java.util.Set;

public class DefaultPropertyState<T> implements PropertyState<T> {

    private final DefaultTaskDependency buildDependency;
    private T value;

    public DefaultPropertyState(TaskResolver taskResolver) {
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    @Override
    public void set(T value) {
        this.value = value;
    }

    @Internal
    @Override
    public T get() {
        return value;
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
    public ConfigurableProvider setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    public ConfigurableProvider<T> builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultPropertyState<?> that = (DefaultPropertyState<?>) o;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return 31 * (value != null ? value.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("value: %s", value);
    }
}