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

import java.util.*;

/**
 * <p>A {@link TaskDependencyResolveContext} which caches the dependencies for each {@link
 * org.gradle.api.tasks.TaskDependency} and {@link org.gradle.api.Buildable} instance during traversal of task
 * dependencies.</p>
 *
 * <p>Supported types:</p> <ul>
 *
 * <li>{@link org.gradle.api.Task}</li>
 *
 * <li>{@link org.gradle.api.tasks.TaskDependency}</li>
 *
 * <li>{@link org.gradle.api.internal.tasks.TaskDependencyInternal}</li>
 *
 * <li>{@link org.gradle.api.Buildable}</li>
 *
 * </ul>
 */
public class CachingTaskDependencyResolveContext implements TaskDependencyResolveContext, TaskDependency {
    private final LinkedList<Object> queue = new LinkedList<Object>();
    private Map<Object, Set<? extends Task>> cache = new HashMap<Object, Set<? extends Task>>();
    private Task task;

    public CachingTaskDependencyResolveContext() {
        cache = new HashMap<Object, Set<? extends Task>>();
    }

    private CachingTaskDependencyResolveContext(Task task, Map<Object, Set<? extends Task>> cache) {
        this.task = task;
        this.cache = cache;
    }

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
            return doResolve();
        } finally {
            queue.clear();
            this.task = null;
        }
    }

    private Set<Task> doResolve() {
        Set<Task> tasks = new LinkedHashSet<Task>();
        while (!queue.isEmpty()) {
            Object dependency = queue.remove(0);
            Set<? extends Task> resolvedDependencies = cache.get(dependency);
            if (resolvedDependencies != null) {
                tasks.addAll(resolvedDependencies);
            } else if (dependency instanceof Task) {
                tasks.add((Task) dependency);
            } else {
                Set<Task> dependencies = doResolveComposite(dependency);
                cache.put(dependency, dependencies);
                tasks.addAll(dependencies);
            }
        }
        return tasks;
    }

    private Set<Task> doResolveComposite(Object dependency) {
        if (dependency instanceof TaskDependencyInternal) {
            TaskDependencyInternal taskDependency = (TaskDependencyInternal) dependency;
            CachingTaskDependencyResolveContext nestedContext = new CachingTaskDependencyResolveContext(task, cache);
            taskDependency.resolve(nestedContext);
            return nestedContext.doResolve();
        } else if (dependency instanceof TaskDependency) {
            TaskDependency taskDependency = (TaskDependency) dependency;
            return new LinkedHashSet<Task>(taskDependency.getDependencies(task));
        } else if (dependency instanceof Buildable) {
            Buildable buildable = (Buildable) dependency;
            CachingTaskDependencyResolveContext nestedContext = new CachingTaskDependencyResolveContext(task, cache);
            nestedContext.add(buildable.getBuildDependencies());
            return nestedContext.doResolve();
        } else {
            throw new IllegalArgumentException(String.format("Cannot resolve object of unknown type %s to a Task.",
                    dependency.getClass().getSimpleName()));
        }
    }

    public void add(Object dependency) {
        queue.add(dependency);
    }
}
