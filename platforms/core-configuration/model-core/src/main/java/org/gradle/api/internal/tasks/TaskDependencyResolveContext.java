/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.artifacts.transform.TransformNodeDependency;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public interface TaskDependencyResolveContext extends Action<Task> {
    @Override
    default void execute(Task task) {
        add(task);
    }

    /**
     * Adds an object that can contribute tasks to the result. Supported types:
     *
     * <ul>
     *
     * <li>{@link org.gradle.api.Task}</li>
     *
     * <li>{@link org.gradle.api.tasks.TaskDependency}</li>
     *
     * <li>{@link org.gradle.api.internal.tasks.TaskDependencyContainer}</li>
     *
     * <li>{@link org.gradle.api.Buildable}</li>
     *
     * <li>{@link TransformNodeDependency}</li>
     *
     * <li>{@link WorkNodeAction}</li>
     *
     * </ul>
     */
    void add(Object dependency);

    /**
     * Visits a failure to visit the dependencies of an object.
     */
    void visitFailure(Throwable failure);

    /**
     * Returns the task whose dependencies are being visited.
     */
    @Nullable
    Task getTask();

    /**
     * If in parallel mode and the given target project (identified by build-tree identity path)
     * is not the current project, defers the task lookup and returns {@code true}.
     * Returns {@code false} if the target is the current project or if not in parallel mode.
     */
    default boolean deferCrossProjectTaskVisitIfNeeded(Path targetProjectIdentityPath, String taskName) {
        return false;
    }

    /**
     * If in parallel mode and the given task path targets another project, defers its
     * resolution and returns {@code true}. Returns {@code false} if the path targets the
     * current project or if not in parallel mode.
     */
    default boolean deferCrossProjectTaskPathIfNeeded(Path taskPath) {
        return false;
    }

    /**
     * If in parallel mode, defers a dependency visit that needs access to all projects
     * (e.g. searching tasks by name across the entire build) and returns {@code true}.
     * The provided action captures the original resolution logic and will be re-executed
     * later under proper locking.
     * Returns {@code false} if not in parallel mode.
     */
    default boolean deferAllProjectsDependencyVisitIfNeeded(Consumer<TaskDependencyResolveContext> resolutionAction) {
        return false;
    }
}
