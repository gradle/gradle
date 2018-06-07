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
import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.execution.taskgraph.TaskInfoFactory;
import org.gradle.execution.taskgraph.WorkInfo;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
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
@NonNullApi
public class CachingTaskDependencyResolveContext<T> implements TaskDependencyResolveContext {
    private final Deque<Object> queue = new ArrayDeque<Object>();
    private final CachingDirectedGraphWalker<Object, T> walker;
    private Task task;

    public CachingTaskDependencyResolveContext(Collection<WorkResolver<T>> workResolvers) {
        this.walker = new CachingDirectedGraphWalker<Object, T>(new TaskGraphImpl(workResolvers));
    }

    public Set<T> getDependencies(@Nullable Task task, Object dependencies) {
        this.task = task;
        try {
            walker.add(dependencies);
            return walker.findValues();
        } catch (Exception e) {
            throw new TaskDependencyResolveException(String.format("Could not determine the dependencies of %s.", task), e);
        } finally {
            queue.clear();
            this.task = null;
        }
    }

    @Nullable
    public Task getTask() {
        return task;
    }

    @Override
    public void add(Object dependency) {
        Preconditions.checkNotNull(dependency);
        queue.add(dependency);
    }

    private class TaskGraphImpl implements DirectedGraph<Object, T> {
        private final Collection<WorkResolver<T>> workResolvers;

        public TaskGraphImpl(Collection<WorkResolver<T>> workResolvers) {
            this.workResolvers = workResolvers;
        }

        @Override
        public void getNodeValues(Object node, final Collection<? super T> values, Collection<? super Object> connectedNodes) {
            if (node instanceof TaskDependencyContainer) {
                TaskDependencyContainer taskDependency = (TaskDependencyContainer) node;
                queue.clear();
                taskDependency.visitDependencies(CachingTaskDependencyResolveContext.this);
                connectedNodes.addAll(queue);
            } else if (node instanceof Buildable) {
                Buildable buildable = (Buildable) node;
                connectedNodes.add(buildable.getBuildDependencies());
            } else {
                boolean handled = false;
                for (WorkResolver<T> workResolver : workResolvers) {
                    if (workResolver.resolve(task, node, connectedNodes, new Action<T>() {
                        @Override
                        public void execute(T resolvedValue) {
                            values.add(resolvedValue);
                        }
                    })) {
                        handled = true;
                        break;
                    }
                }
                if (!handled) {
                    throw new IllegalArgumentException(String.format("Cannot resolve object of unknown type %s to a Task.",
                        node.getClass().getSimpleName()));
                }
            }
        }
    }

    public interface WorkResolver<T> {
        boolean resolve(Task task, Object node, Collection<Object> connectedNodes, Action<? super T> resolveAction);
    }

    public static class TaskResolver implements WorkResolver<Task> {
        @Override
        public boolean resolve(Task task, Object node, Collection<Object> connectedNodes, Action<? super Task> resolveAction) {
            return resolveToTask(task, node, connectedNodes, resolveAction);
        }
    }

    public static class TaskInfoResolver implements WorkResolver<WorkInfo> {
        private final TaskInfoFactory taskInfoFactory;

        public TaskInfoResolver(TaskInfoFactory taskInfoFactory) {
            this.taskInfoFactory = taskInfoFactory;
        }

        @Override
        public boolean resolve(Task task, Object node, Collection<Object> connectedNodes, final Action<? super WorkInfo> resolveAction) {
            return resolveToTask(task, node, connectedNodes, new Action<Task>() {
                @Override
                public void execute(Task task) {
                    resolveAction.execute(taskInfoFactory.getOrCreateNode(task));
                }
            });
        }
    }

    private static boolean resolveToTask(@Nullable Task originalTask, Object node, Collection<Object> connectedNodes, Action<? super Task> resolveAction) {
        if (node instanceof TaskDependency) {
            TaskDependency taskDependency = (TaskDependency) node;
            for (Task dependencyTask : taskDependency.getDependencies(originalTask)) {
                resolveAction.execute(dependencyTask);
            }
        } else if (node instanceof Task) {
            resolveAction.execute((Task) node);
        } else if (node instanceof TaskReferenceInternal) {
            Task task = ((TaskReferenceInternal) node).resolveTask();
            resolveAction.execute(task);
        } else {
            return false;
        }
        return true;
    }
}
