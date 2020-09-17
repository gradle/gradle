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
import org.gradle.internal.build.BuildState;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class TaskInAnotherBuild extends TaskNode {

    private final IncludedBuildTaskGraph taskGraph;

    public static TaskInAnotherBuild of(
        TaskInternal task,
        BuildIdentifier currentBuildId,
        IncludedBuildTaskGraph taskGraph
    ) {
        BuildIdentifier targetBuild = ((ProjectInternal) task.getProject()).getServices().get(BuildState.class).getBuildIdentifier();
        return new ResolvedTaskInAnotherBuild(task, currentBuildId, taskGraph, targetBuild);
    }

    public static TaskInAnotherBuild ofUnresolved(
        Path taskIdentityPath,
        String taskPath,
        BuildIdentifier thisBuild,
        BuildIdentifier targetBuild,
        IncludedBuildTaskGraph taskGraph
    ) {
        return new UnresolvedTaskInAnotherBuild(taskIdentityPath, taskPath, thisBuild, targetBuild, taskGraph);
    }

    protected IncludedBuildTaskResource.State state = IncludedBuildTaskResource.State.WAITING;
    private final BuildIdentifier thisBuild;
    private final BuildIdentifier targetBuild;

    protected TaskInAnotherBuild(BuildIdentifier thisBuild, BuildIdentifier targetBuild, IncludedBuildTaskGraph taskGraph) {
        this.thisBuild = thisBuild;
        this.targetBuild = targetBuild;
        doNotRequire();
        this.taskGraph = taskGraph;
    }

    public BuildIdentifier getThisBuild() {
        return thisBuild;
    }

    public BuildIdentifier getTargetBuild() {
        return targetBuild;
    }

    public abstract String getTaskPath();

    public abstract Path getTaskIdentityPath();

    @Override
    public void prepareForExecution() {
        // Should get back some kind of reference that can be queried below instead of looking the task up every time
        taskGraph.addTask(thisBuild, targetBuild, getTaskPath());
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
        // Ignore. Currently the actions don't need to run, it's just better if they do
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
    public void require() {
        // Ignore
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

        state = taskGraph.getTaskState(targetBuild, getTaskPath());
        return state != IncludedBuildTaskResource.State.WAITING;
    }

    @Override
    public int compareTo(Node other) {
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        TaskInAnotherBuild taskNode = (TaskInAnotherBuild) other;
        return getTaskIdentityPath().compareTo(taskNode.getTaskIdentityPath());
    }

    @Override
    public String toString() {
        return getTaskIdentityPath().toString();
    }

    @Override
    public void resolveMutations() {
        // Assume for now that no task in the consuming build will destroy the outputs of this task or overlaps with this task
    }

    private static class ResolvedTaskInAnotherBuild extends TaskInAnotherBuild {
        private final TaskInternal task;

        ResolvedTaskInAnotherBuild(
            TaskInternal task,
            BuildIdentifier thisBuild,
            IncludedBuildTaskGraph taskGraph,
            BuildIdentifier targetBuild
        ) {
            super(thisBuild, targetBuild, taskGraph);
            this.task = task;
        }

        @Override
        public TaskInternal getTask() {
            // Expose the task to build logic (for now)
            return task;
        }

        @Override
        public Path getTaskIdentityPath() {
            return getTask().getIdentityPath();
        }

        @Override
        public String getTaskPath() {
            return task.getPath();
        }
    }

    private static class UnresolvedTaskInAnotherBuild extends TaskInAnotherBuild {

        private final Path taskIdentityPath;
        private final String taskPath;

        public UnresolvedTaskInAnotherBuild(
            Path taskIdentityPath,
            String taskPath,
            BuildIdentifier thisBuild,
            BuildIdentifier targetBuild,
            IncludedBuildTaskGraph taskGraph
        ) {
            super(thisBuild, targetBuild, taskGraph);
            this.taskIdentityPath = taskIdentityPath;
            this.taskPath = taskPath;
        }

        @Override
        public Path getTaskIdentityPath() {
            return taskIdentityPath;
        }

        @Override
        public String getTaskPath() {
            return taskPath;
        }

        @Override
        public TaskInternal getTask() {
            throw new UnsupportedOperationException();
        }
    }
}
