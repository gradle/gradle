/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.model.internal.core.ModelPath;

import java.util.Map;

public interface TaskContainerInternal extends TaskContainer, TaskResolver, PolymorphicDomainObjectContainerInternal<Task> {

    // The path to the project's task container in the model registry
    public ModelPath MODEL_PATH = ModelPath.path("tasks");

    DynamicObject getTasksAsDynamicObject();

    /**
     * <p>Add placeholder action if task is referenced by name that does not (yet) exist.
     * If a task is referenced by name and not listed as task, the provided action is executed and the task name is looked up again before proceeding
     * This allows lazy application of plugins if task is referenced but not yet part of the taskcontainer.</p>
     *
     * @param placeholderName the placeholderName that references the placeholder action.
     * @param runnable the Runnable executed when referencing a task that does not exist, but a placeholder with the given name is defined.
     */
    void addPlaceholderAction(String placeholderName, Runnable runnable);

    /**
     * Force the entire graph to come into existence.
     *
     * Tasks may have dependencies that are abstract (e.g. a dependency on a task _name_).
     * Calling this method will force all task dependencies to be actualised, which may mean new tasks are
     * created because of things like task rules etc.
     *
     * As part of this, all placeholder actions are materialized to show up in 'tasks' and 'tasks --all' overview.
     */
    void actualize();

    Map<String, Runnable> getPlaceholderActions();
}
