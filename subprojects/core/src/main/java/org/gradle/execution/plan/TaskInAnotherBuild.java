/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.composite.internal.IncludedBuildTaskResource;
import org.gradle.internal.Actions;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class TaskInAnotherBuild extends TaskNode {
    public static TaskInAnotherBuild of(
        TaskInternal task,
        IncludedBuildTaskGraph taskGraph
    ) {
        BuildIdentifier targetBuild = buildIdentifierOf(task);
        IncludedBuildTaskResource taskResource = taskGraph.locateTask(targetBuild, task);
        return new TaskInAnotherBuild(task.getIdentityPath(), task.getPath(), targetBuild, taskResource);
    }

    public static TaskInAnotherBuild of(
        String taskPath,
        BuildIdentifier targetBuild,
        IncludedBuildTaskGraph taskGraph
    ) {
        IncludedBuildTaskResource taskResource = taskGraph.locateTask(targetBuild, taskPath);
        Path taskIdentityPath = Path.path(targetBuild.getName()).append(Path.path(taskPath));
        return new TaskInAnotherBuild(taskIdentityPath, taskPath, targetBuild, taskResource);
    }

    protected IncludedBuildTaskResource.State state = IncludedBuildTaskResource.State.WAITING;
    private final Path taskIdentityPath;
    private final String taskPath;
    private final BuildIdentifier targetBuild;
    private final IncludedBuildTaskResource target;

    protected TaskInAnotherBuild(Path taskIdentityPath, String taskPath, BuildIdentifier targetBuild, IncludedBuildTaskResource target) {
        this.taskIdentityPath = taskIdentityPath;
        this.taskPath = taskPath;
        this.targetBuild = targetBuild;
        this.target = target;
        doNotRequire();
    }

    public BuildIdentifier getTargetBuild() {
        return targetBuild;
    }

    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public TaskInternal getTask() {
        return target.getTask();
    }

    @Override
    public void prepareForExecution() {
        target.queueForExecution();
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        // Ignore, as the node in the other build's execution graph takes care of this
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        // Ignore, as the node in the other build's execution graph takes care of this
        return null;
    }

    @Override
    public List<ResourceLock> getResourcesToLock() {
        // Ignore, as the node in the other build's execution graph will take care of this
        return Collections.emptyList();
    }

    @Override
    public Throwable getNodeFailure() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rethrowNodeFailure() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendPostAction(Action<? super Task> action) {
        // Ignore. Currently, the actions don't need to run, it's just better if they do
        // By the time this node is notified that the task in the other build has completed, it's too late to run the action
        // Instead, the action should be attached to the task in the other build rather than here
    }

    @Override
    public Action<? super Task> getPostAction() {
        return Actions.doNothing();
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
    }

    @Override
    public boolean requiresMonitoring() {
        return true;
    }

    @Override
    public boolean isSuccessful() {
        return state == IncludedBuildTaskResource.State.SUCCESS;
    }

    @Override
    public boolean isFailed() {
        return state == IncludedBuildTaskResource.State.FAILED;
    }

    @Override
    public boolean isComplete() {
        if (state != IncludedBuildTaskResource.State.WAITING) {
            return true;
        }

        state = target.getTaskState();
        return state != IncludedBuildTaskResource.State.WAITING;
    }

    @Override
    public int compareTo(Node other) {
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        TaskInAnotherBuild taskNode = (TaskInAnotherBuild) other;
        return taskIdentityPath.compareTo(taskNode.taskIdentityPath);
    }

    @Override
    public String toString() {
        return taskIdentityPath.toString();
    }

    @Override
    public void resolveMutations() {
        // Assume for now that no task in the consuming build will destroy the outputs of this task or overlaps with this task
    }

    private static BuildIdentifier buildIdentifierOf(TaskInternal task) {
        return ((ProjectInternal) task.getProject()).getOwner().getOwner().getBuildIdentifier();
    }
}
