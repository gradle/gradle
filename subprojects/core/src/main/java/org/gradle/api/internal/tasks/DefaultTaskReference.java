/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.tasks.TaskReference;
import org.gradle.util.Path;

/**
 * Default implementation of {@link TaskReference}.
 */
public class DefaultTaskReference implements TaskReference, TaskDependencyContainer {

    private final String taskName;
    private final TaskDependencyContainer taskDependency;

    private DefaultTaskReference(String taskName, TaskDependencyContainer taskDependency) {
        this.taskName = taskName;
        this.taskDependency = taskDependency;
    }

    @Override
    public String getName() {
        return taskName;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(taskDependency);
    }

    /**
     * Create a {@link TaskReference} to a task, and use the provided {@link TaskDependencyFactory}
     * to resolve the provided absolute task path string.
     * <p>
     * The task path is evaluated lazily, meaning the referenced task will only be looked-up in
     * the target project when the returned task reference's dependencies are visited.
     *
     * @throws IllegalArgumentException If the provided task path string is not absolute or does not represent
     * a valid task path.
     */
    public static TaskReference create(
        String pathStr,
        TaskDependencyFactory taskDependencyFactory
    ) {
        Path path = Path.path(pathStr);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(String.format("Task path '%s' is not a qualified task path (e.g. ':task' or ':project:task').", pathStr));
        }
        String taskName = path.getName();
        if (taskName == null) {
            assert path == Path.ROOT;
            throw new IllegalArgumentException("The root path is not a valid task path");
        }
        TaskDependencyContainer taskDependency = taskDependencyFactory.configurableDependency(ImmutableSet.of(pathStr));
        return new DefaultTaskReference(taskName, taskDependency);
    }

}
