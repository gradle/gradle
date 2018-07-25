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

package org.gradle.execution.taskgraph;

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;

import java.util.Set;

/**
 * A {@link TaskInfo} implementation for a task in the current build.
 */
public class LocalTaskInfo extends TaskInfo {
    private final TaskInternal task;

    public LocalTaskInfo(TaskInternal task) {
        this.task = task;
    }

    public TaskInternal getTask() {
        return task;
    }

    @Override
    public void collectTaskInto(ImmutableCollection.Builder<Task> builder) {
        builder.add(task);
    }

    public Throwable getWorkFailure() {
        return task.getState().getFailure();
    }

    @Override
    public void rethrowFailure() {
        task.getState().rethrowFailure();
    }

    @Override
    public void prepareForExecution() {
        ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
        for (WorkInfo targetNode : getDependencies(dependencyResolver)) {
            addDependencySuccessor(targetNode);
            processHardSuccessor.execute(targetNode);
        }
        for (WorkInfo targetNode : getFinalizedBy(dependencyResolver)) {
            if (!(targetNode instanceof TaskInfo)) {
                throw new IllegalStateException("Only tasks can be finalizers: " + targetNode);
            }
            addFinalizerNode((TaskInfo) targetNode);
            processHardSuccessor.execute(targetNode);
        }
        for (WorkInfo targetNode : getMustRunAfter(dependencyResolver)) {
            addMustSuccessor(targetNode);
        }
        for (WorkInfo targetNode : getShouldRunAfter(dependencyResolver)) {
            addShouldSuccessor(targetNode);
        }
    }

    private void addFinalizerNode(TaskInfo finalizerNode) {
        addFinalizer(finalizerNode);
        if (!finalizerNode.isInKnownState()) {
            finalizerNode.mustNotRun();
        }
    }

    private Set<WorkInfo> getDependencies(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getTaskDependencies());
    }

    private Set<WorkInfo> getFinalizedBy(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getFinalizedBy());
    }

    private Set<WorkInfo> getMustRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getMustRunAfter());
    }

    private Set<WorkInfo> getShouldRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getShouldRunAfter());
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public int compareTo(WorkInfo other) {
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        LocalTaskInfo localTask = (LocalTaskInfo) other;
        return task.compareTo(localTask.task);
    }

    @Override
    public String toString() {
        return task.getIdentityPath().toString();
    }
}
