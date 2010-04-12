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

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * A {@link TaskDependencyResolveContext} which caches the dependencies for each {@link
 * org.gradle.api.tasks.TaskDependency} and {@link org.gradle.api.Buildable} instance during traversal of task
 * dependencies.
 */
public class CachingTaskDependencyResolveContext implements TaskDependencyResolveContext, TaskDependency {
    private final LinkedList<Object> queue = new LinkedList<Object>();
    private Task task;

    public Set<? extends Task> getDependencies(Task task) {
        add(task.getTaskDependencies());
        return resolve(task);
    }

    public Task getTask() {
        return task;
    }

    public Set<Task> resolve(Task task) {
        this.task = task;
        try {
            Set<Task> tasks = new LinkedHashSet<Task>();
            doResolve(task, tasks);
            return tasks;
        } finally {
            queue.clear();
            this.task = null;
        }
    }

    private void doResolve(Task task, Set<Task> tasks) {
        while (!queue.isEmpty()) {
            Object dependency = queue.remove(0);
            if (dependency instanceof Task) {
                tasks.add((Task) dependency);
            } else if (dependency instanceof TaskDependencyInternal) {
                TaskDependencyInternal taskDependency = (TaskDependencyInternal) dependency;
                taskDependency.resolve(this);
            } else if (dependency instanceof TaskDependency) {
                TaskDependency taskDependency = (TaskDependency) dependency;
                tasks.addAll(taskDependency.getDependencies(task));
            } else if (dependency instanceof Buildable) {
                Buildable buildable = (Buildable) dependency;
                queue.add(0, buildable.getBuildDependencies());
            } else {
                throw new IllegalArgumentException(String.format("Cannot resolve object of unknown type %s to a Task.",
                        dependency.getClass().getSimpleName()));
            }
        }
    }

    public void add(Object dependency) {
        queue.add(dependency);
    }
}
