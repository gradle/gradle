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
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class TaskInAnotherBuild extends TaskNode implements SelfExecutingNode {
    public static TaskInAnotherBuild of(
        TaskInternal task,
        BuildTreeWorkGraphController taskGraph
    ) {
        BuildIdentifier targetBuild = buildIdentifierOf(task);
        TaskIdentifier taskIdentifier = new TaskIdentifier(targetBuild, task);
        IncludedBuildTaskResource taskResource = taskGraph.locateTask(taskIdentifier);
        return new TaskInAnotherBuild(task.getIdentityPath(), task.getPath(), targetBuild) {
            @Override
            protected IncludedBuildTaskResource getTarget() {
                return taskResource;
            }
        };
    }

    /**
     * Creates a reference to a task in another build, restored from the configuration cache.
     *
     * The reference must be {@link Restored#bindTarget bound} to its restored target node once all
     * builds in the tree have been loaded. The target task is located on-demand, once the work graph
     * is being scheduled, to allow for cycles between builds stored to the configuration cache.
     *
     * @param taskPath the path to the task relative to its build
     * @param targetBuild the build containing the task
     * @param taskGraph the task graph where the task should be located
     */
    public static Restored restored(
        String taskPath,
        BuildIdentifier targetBuild,
        BuildTreeWorkGraphController taskGraph
    ) {
        Path taskIdentityPath = Path.path(targetBuild.getBuildPath()).append(Path.path(taskPath));
        return new Restored(taskIdentityPath, taskPath, targetBuild, taskGraph);
    }

    /**
     * A reference restored from the configuration cache.
     */
    public static class Restored extends TaskInAnotherBuild {

        private final BuildTreeWorkGraphController taskGraph;
        private final Lazy<IncludedBuildTaskResource> target;

        private @Nullable TaskNode targetNode;

        private Restored(
            Path taskIdentityPath,
            String taskPath,
            BuildIdentifier targetBuild,
            BuildTreeWorkGraphController taskGraph
        ) {
            super(taskIdentityPath, taskPath, targetBuild);
            this.taskGraph = taskGraph;

            this.target = Lazy.unsafe().of(this::locateTarget);
        }

        /**
         * Binds this reference to its restored target node.
         */
        public void bindTarget(TaskNode targetNode) {
            this.targetNode = targetNode;
        }

        private IncludedBuildTaskResource locateTarget() {
            if (targetNode == null) {
                throw new IllegalStateException("No target node has been bound for " + this);
            }
            return taskGraph.locateTask(new TaskIdentifier(getTargetBuild(), targetNode.getTask()));
        }

        @Override
        protected IncludedBuildTaskResource getTarget() {
            return target.get();
        }

    }

    private IncludedBuildTaskResource.State taskState = IncludedBuildTaskResource.State.Scheduled;
    private final Path taskIdentityPath;
    private final String taskPath;
    private final BuildIdentifier targetBuild;

    protected TaskInAnotherBuild(Path taskIdentityPath, String taskPath, BuildIdentifier targetBuild) {
        this.taskIdentityPath = taskIdentityPath;
        this.taskPath = taskPath;
        this.targetBuild = targetBuild;
    }

    public BuildIdentifier getTargetBuild() {
        return targetBuild;
    }

    public String getTaskPath() {
        return taskPath;
    }

    public Path getTaskIdentityPath() {
        return taskIdentityPath;
    }

    @Override
    public TaskInternal getTask() {
        return getTarget().getTask();
    }

    @Override
    protected ExecutionState getInitialState() {
        switch (getTarget().getTaskState()) {
            case Scheduled:
            case NotScheduled:
                return null;
            case Success:
                return ExecutionState.EXECUTED;
            case Failed:
                return ExecutionState.FAILED_DEPENDENCY;
            default:
                throw new IllegalStateException();
        }
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
        getTarget().onComplete(() -> monitor.execute(this));
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
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        getTarget().queueForExecution();
    }

    @Override
    public DependenciesState doCheckDependenciesComplete() {
        DependenciesState dependenciesState = super.doCheckDependenciesComplete();
        if (dependenciesState != DependenciesState.COMPLETE_AND_SUCCESSFUL) {
            return dependenciesState;
        }

        // This node is ready to "execute" when the task in the other build has completed
        if (!taskState.isComplete()) {
            taskState = getTarget().getTaskState();
        }
        switch (taskState) {
            case Scheduled:
                return DependenciesState.NOT_COMPLETE;
            case Success:
                return DependenciesState.COMPLETE_AND_SUCCESSFUL;
            case Failed:
            case NotScheduled:
                return DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "other build task " + taskIdentityPath;
    }

    @Override
    protected void nodeSpecificHealthDiagnostics(StringBuilder builder) {
        super.nodeSpecificHealthDiagnostics(builder);
        builder.append(", taskState=").append(taskState).append(", ").append(getTarget().healthDiagnostics());
    }

    @Override
    public void execute(NodeExecutionContext context) {
        // This node does not do anything itself
    }

    private static BuildIdentifier buildIdentifierOf(TaskInternal task) {
        return ((ProjectInternal) task.getProject()).getOwner().getOwner().getBuildIdentifier();
    }

    protected abstract IncludedBuildTaskResource getTarget();

    public TaskNode getTargetNode() {
        return getTarget().getTaskNode();
    }
}
