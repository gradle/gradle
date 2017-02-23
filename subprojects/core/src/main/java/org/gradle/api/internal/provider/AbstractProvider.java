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
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;

public abstract class AbstractProvider<T> implements Provider<T> {

    private final DefaultTaskDependency taskDependency;

    public AbstractProvider(TaskResolver taskResolver) {
        taskDependency = new DefaultTaskDependency(taskResolver);
    }

    @Internal
    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    @Override
    public Provider<T> builtBy(Object... tasks) {
        taskDependency.add(tasks);
        return this;
    }

    @Override
    public String toString() {
        return String.format("value: %s", get());
    }
}