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
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public interface TaskContainerInternal extends TaskContainer, TaskResolver, PolymorphicDomainObjectContainerInternal<Task> {

    // The path to the project's task container in the model registry
    ModelPath MODEL_PATH = ModelPath.path("tasks");
    ModelType<TaskContainerInternal> MODEL_TYPE = ModelType.of(TaskContainerInternal.class);

    DynamicObject getTasksAsDynamicObject();

    /**
     * Force the task graph to come into existence.
     */
    void realize();

    /**
     * Performs work to discover more tasks.
     *
     * This method differs from {@link #realize} in that it does not realize the whole subtree.
     */
    void discoverTasks();

    /**
     * Ensures that all configuration has been applied to the given task, and the task is ready to be added to the task graph.
     */
    void prepareForExecution(Task task);

    /**
     * Adds a previously constructed task into the container.  For internal use with software model bridging.
     */
    boolean addInternal(Task task);

    /**
     * Adds a previously constructed task into the container.  For internal use with software model bridging.
     */
    boolean addAllInternal(Collection<? extends Task> task);

    /**
     * Creates an instance of the given task type without invoking its constructors. This is used to recreate a task instance from the configuration cache.
     *
     * TODO:configuration-cache - review this
     */
    <T extends Task> T createWithoutConstructor(String name, Class<T> type, long uniqueId);
}
