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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.util.Path;

import java.util.Collection;

/**
 * A {@link TaskInfo} implementation for a task in the current build.
 */
public class LocalTaskInfo extends TaskInfo {
    private final TaskInternal task;

    public LocalTaskInfo(TaskInternal task) {
        this.task = task;
    }

    @Override
    public Path getIdentityPath() {
        return task.getIdentityPath();
    }

    public TaskInternal getTask() {
        return task;
    }

    @Override
    public void collectTaskInto(ImmutableSet.Builder<Task> builder) {
        builder.add(task);
    }

    public Throwable getTaskFailure() {
        return task.getState().getFailure();
    }

    @Override
    public boolean satisfies(Spec<? super Task> filter) {
        return filter.isSatisfiedBy(task);
    }

    @Override
    public void prepareForExecution() {
        ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
    }

    @Override
    public Collection<? extends TaskInfo> getDependencies(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getTaskDependencies());
    }

    @Override
    public Collection<? extends TaskInfo> getFinalizedBy(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getFinalizedBy());
    }

    @Override
    public Collection<? extends TaskInfo> getMustRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getMustRunAfter());
    }

    @Override
    public Collection<? extends TaskInfo> getShouldRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getShouldRunAfter());
    }

    @Override
    public int compareTo(TaskInfo other) {
        if (other.getClass() != getClass()) {
            return -1;
        }
        LocalTaskInfo localTask = (LocalTaskInfo) other;
        return task.compareTo(localTask.task);
    }
}
