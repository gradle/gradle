/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.tasks.TaskDependency;

public interface WorkDependencyResolver<T> {
    /**
     * Resolves dependencies to a specific type.
     *
     * @return {@code true} if this resolver could resolve the given node, {@code false} otherwise.
     */
    boolean resolve(Task task, Object node, Action<? super T> resolveAction);

    boolean attachActionTo(T value, Action<? super Task> action);

    /**
     * Resolves dependencies to {@link Task} objects.
     */
    WorkDependencyResolver<Task> TASK_AS_TASK = new WorkDependencyResolver<Task>() {
        @Override
        public boolean resolve(Task originalTask, Object node, Action<? super Task> resolveAction) {
            if (node instanceof TaskDependency) {
                TaskDependency taskDependency = (TaskDependency) node;
                for (Task dependencyTask : taskDependency.getDependencies(originalTask)) {
                    resolveAction.execute(dependencyTask);
                }
                return true;
            }
            if (node instanceof Task) {
                resolveAction.execute((Task) node);
                return true;
            }
            return false;
        }

        @Override
        public boolean attachActionTo(Task task, Action<? super Task> action) {
            return false;
        }
    };
}
