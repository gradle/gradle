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
import org.gradle.api.internal.artifacts.transform.TransformationDependency;

import javax.annotation.Nullable;

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
     * <li>{@link TransformationDependency}</li>
     *
     * <li>{@link FinalizeAction}</li>
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
}
