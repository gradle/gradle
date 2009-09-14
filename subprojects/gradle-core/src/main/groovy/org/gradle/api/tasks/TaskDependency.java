/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.tasks;

import org.gradle.api.Task;

import java.util.Set;

/**
 * <p>A <code>TaskDependency</code> represents an <em>unordered</em> set of tasks which a {@link Task} depends on.
 * Gradle ensures that all the dependencies of a task are executed before the task itself is executed.</p>
 *
 * <p>You can add a <code>TaskDependency</code> to a task by calling the task's {@link Task#dependsOn(Object...)}
 * method.</p>
 */
public interface TaskDependency {
    /**
     * <p>Determines the dependencies for the given {@link Task}. This method is called when Gradle assembles the task
     * execution graph for a build. This occurs after all the projects have been evaluated, and before any task
     * execution begins.</p>
     *
     * @param task The task to determine the dependencies for.
     * @return The tasks which the given task depends on. Returns an empty set if the task has no dependencies.
     */
    Set<? extends Task> getDependencies(Task task);
}
