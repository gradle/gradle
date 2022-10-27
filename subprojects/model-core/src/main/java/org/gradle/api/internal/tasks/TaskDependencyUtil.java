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

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.accesscontrol.AllowUsingApiForExternalUse;

import javax.annotation.Nullable;
import java.util.Set;

public class TaskDependencyUtil {
    /**
     * If the {@code taskDependency} implements the {@link TaskDependencyInternal} interface, gets its dependencies through the
     * method intended for internal use, {@link TaskDependencyInternal#getDependenciesForInternalUse(Task)}.
     * Otherwise, falls back to accessing them through the public API, {@link TaskDependency#getDependencies(Task)}.
     *
     * @param taskDependency the instance to get the dependencies from.
     * @param task inherits the semantics from the corresponding parameter of {@link TaskDependency#getDependencies(Task)}
     * @return the set of task dependencies, as {@link TaskDependency#getDependencies(Task)} would.
     */
    @AllowUsingApiForExternalUse
    public static Set<? extends Task> getDependenciesForInternalUse(TaskDependency taskDependency, @Nullable Task task) {
        return taskDependency instanceof TaskDependencyInternal ?
            ((TaskDependencyInternal) taskDependency).getDependenciesForInternalUse(task) :
            taskDependency.getDependencies(task);
    }
}
