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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.composite.internal.IncludedBuildTaskResource;
import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TaskInAnotherBuild extends TaskNode implements SelfExecutingNode {
    public static TaskInAnotherBuild of(
        TaskInternal task,
        BuildTreeWorkGraphController taskGraph
    ) {
        BuildIdentifier targetBuild = buildIdentifierOf(task);
        TaskIdentifier taskIdentifier = TaskIdentifier.of(targetBuild, task);
        IncludedBuildTaskResource taskResource = taskGraph.locateTask(taskIdentifier);
        return new TaskInAnotherBuild(task.getIdentityPath(), task.getPath(), targetBuild, taskResource);
    }

    public static TaskInAnotherBuild of(
        String taskPath,
        BuildIdentifier targetBuild,
        BuildTreeWorkGraphController taskGraph
    ) {
        TaskIdentifier taskIdentifier = TaskIdentifier.of(targetBuild, taskPath);
        IncludedBuildTaskResource taskResource = taskGraph.locateTask(taskIdentifier);
        Path taskIdentityPath = Path.path(targetBuild.getName()).append(Path.path(taskPath));
        return new TaskInAnotherBuild(taskIdentityPath, taskPath, targetBuild, taskResource);
    }

    protected IncludedBuildTaskResource.State state = IncludedBuildTaskResource.State.Waiting;
    private final Path taskIdentityPath;
    private final String taskPath;
    private final BuildIdentifier targetBuild;
    private final IncludedBuildTaskResource target;

    protected TaskInAnotherBuild(Path taskIdentityPath, String taskPath, BuildIdentifier targetBuild, IncludedBuildTaskResource target) {
        this.taskIdentityPath = taskIdentityPath;
        this.taskPath = taskPath;
        this.targetBuild = targetBuild;
        this.target = target;
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
    public Set<Node> getLifecycleSuccessors() {
        return Collections.emptySet();
    }

    @Override
    public void setLifecycleSuccessors(Set<Node> successors) {
        if (!successors.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void prepareForExecution(Action<Node> monitor) {
        target.queueForExecution();
        target.onComplete(() -> monitor.execute(this));
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
        return null;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
    }

    @Override
    public boolean allDependenciesSuccessful() {
        return state == IncludedBuildTaskResource.State.Success;
    }

    @Override
    public boolean isReady() {
        // This node is ready to "execute" when the task in the other build has completed
        if (!super.isReady()) {
            return false;
        }
        if (state.isComplete()) {
            return true;
        }
        state = target.getTaskState();
        return state.isComplete();
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
    public void execute(NodeExecutionContext context) {
        // This node does not do anything itself
    }

    private static BuildIdentifier buildIdentifierOf(TaskInternal task) {
        return ((ProjectInternal) task.getProject()).getOwner().getOwner().getBuildIdentifier();
    }
}
