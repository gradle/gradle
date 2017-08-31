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

import com.google.common.base.Preconditions;
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

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
    private final CachingDirectedGraphWalker<Object, Task> walker = new CachingDirectedGraphWalker<Object, Task>(
            new TaskGraphImpl());
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
            return doResolve();
        } catch (Exception e) {
            throw new TaskDependencyResolveException(String.format("Could not determine the dependencies of %s.", task), e);
        } finally {
            queue.clear();
            this.task = null;
        }
    }

    private Set<Task> doResolve() {
        walker.add(queue);
        return walker.findValues();
    }

    public void add(Object dependency) {
        Preconditions.checkNotNull(dependency);
        queue.add(dependency);
    }

    private class TaskGraphImpl implements DirectedGraph<Object, Task> {
        public void getNodeValues(Object node, Collection<? super Task> values, Collection<? super Object> connectedNodes) {
            if (node instanceof TaskDependencyContainer) {
                TaskDependencyContainer taskDependency = (TaskDependencyContainer) node;
                queue.clear();
                taskDependency.visitDependencies(CachingTaskDependencyResolveContext.this);
                connectedNodes.addAll(queue);
            } else if (node instanceof Buildable) {
                Buildable buildable = (Buildable) node;
                connectedNodes.add(buildable.getBuildDependencies());
            } else if (node instanceof TaskDependency) {
                TaskDependency dependency = (TaskDependency) node;
                values.addAll(dependency.getDependencies(task));
            } else if (node instanceof Task) {
                values.add((Task) node);
            } else if (node instanceof TaskReference) {
                TaskContainerInternal tasks = (TaskContainerInternal) task.getProject().getTasks();
                Task task = tasks.resolveTask((TaskReference) node);
                values.add(task);
            } else {
                throw new IllegalArgumentException(String.format("Cannot resolve object of unknown type %s to a Task.",
                        node.getClass().getSimpleName()));
            }
        }
    }
}
