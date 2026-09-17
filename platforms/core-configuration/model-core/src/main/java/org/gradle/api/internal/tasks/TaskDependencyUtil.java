/*
 * Copyright 2022 the original author or authors.
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

import static java.util.Arrays.asList;
import static org.gradle.api.internal.tasks.WorkDependencyResolver.TASK_AS_TASK;

public class TaskDependencyUtil {

    private static final WorkDependencyResolver<Task> IGNORE_ARTIFACT_TRANSFORM_RESOLVER = new WorkDependencyResolver<Task>() {
        @Override
        public boolean resolve(Task task, Object node, Action<? super Task> resolveAction) {
            // Ignore artifact transforms
            return node instanceof TransformNodeDependency || node instanceof WorkNodeAction;
        }
    };

    /**
     * Return a new resolver which resolves a {@link TaskDependencyContainer} to the set of
     * Task objects it contains. All other types of work are ignored.
     * <p>
     * This method should only be used to support existing public APIs which incorrectly
     * assume that {@link TaskDependencyContainer}s only contain tasks. To resolve a
     * task dependency container to the full set of work it contains, use a
     * {@link org.gradle.execution.plan.TaskDependencyResolver}.
     */
    public static CachingTaskDependencyResolveContext<Task> newTaskResolver() {
        return new CachingTaskDependencyResolveContext<>(
            asList(TASK_AS_TASK, IGNORE_ARTIFACT_TRANSFORM_RESOLVER)
        );
    }

}
