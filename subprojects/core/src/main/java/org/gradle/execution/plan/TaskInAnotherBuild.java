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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class TaskInAnotherBuild extends TaskNode {

    public static TaskInAnotherBuild of(
        TaskInternal task,
        BuildTreeWorkGraphController taskGraph
    ) {
        TaskNode targetNode = taskGraph.locateTaskNode(task);
        return new TaskInAnotherBuild(task.getIdentityPath()) {

            @Override
            public TaskNode getTargetNode() {
                return targetNode;
            }

            @Override
            protected void queueTargetForExecution() {
                taskGraph.queueForExecution(task);
            }

        };

    }

    /**
     * Creates a reference to a task in another build, restored from the configuration cache.
     *
     * The reference must be {@link Restored#bindTarget bound} to its restored target node once all
     * builds in the tree have been loaded.
     *
     * @param taskIdentityPath the path to the task relative to its build tree
     */
    public static Restored restored(Path taskIdentityPath) {
        return new Restored(taskIdentityPath);
    }

    /**
     * A reference restored from the configuration cache.
     */
    public static class Restored extends TaskInAnotherBuild {

        private @Nullable TaskNode targetNode;

        private Restored(Path taskIdentityPath) {
            super(taskIdentityPath);
        }

        /**
         * Binds this reference to its restored target node.
         */
        public void bindTarget(TaskNode targetNode) {
            this.targetNode = targetNode;
        }

        @Override
        public TaskNode getTargetNode() {
            if (targetNode == null) {
                throw new IllegalStateException("No target node has been bound for " + this);
            }
            return targetNode;
        }

        @Override
        protected void queueTargetForExecution() {
            // Nothing to queue. The target node is always part of its build's restored work graph
        }

    }

    private @Nullable DependenciesState targetOutcome;
    private final Path taskIdentityPath;

    protected TaskInAnotherBuild(Path taskIdentityPath) {
        this.taskIdentityPath = taskIdentityPath;
    }

    public Path getTaskIdentityPath() {
        return taskIdentityPath;
    }

    /**
     * The node for the target task, in the other build's work graph.
     */
    public abstract TaskNode getTargetNode();

    @Override
    public TaskInternal getTask() {
        return getTargetNode().getTask();
    }

    @Override
    protected @Nullable ExecutionState getInitialState() {
        TaskNode targetNode = getTargetNode();
        if (targetNode.isExecuted()) {
            if (targetNode.isSuccessful()) {
                return ExecutionState.EXECUTED;
            } else {
                return ExecutionState.FAILED_DEPENDENCY;
            }
        }
        return null;
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
        getTargetNode().onComplete(() -> monitor.execute(this));
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
        queueTargetForExecution();
    }

    /**
     * Queues the target task for execution in its build's work graph.
     */
    protected abstract void queueTargetForExecution();

    @Override
    public DependenciesState doCheckDependenciesComplete() {
        DependenciesState dependenciesState = super.doCheckDependenciesComplete();
        if (dependenciesState != DependenciesState.COMPLETE_AND_SUCCESSFUL) {
            return dependenciesState;
        }

        // This node is ready to "execute" when the task in the other build has completed
        if (targetOutcome == null) {
            TaskNode targetNode = getTargetNode();
            if (!targetNode.isComplete()) {
                return DependenciesState.NOT_COMPLETE;
            }
            targetOutcome = targetNode.isExecuted() && targetNode.isSuccessful()
                ? DependenciesState.COMPLETE_AND_SUCCESSFUL
                : DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL;
        }
        return targetOutcome;
    }

    @Override
    public String toString() {
        return "other build task " + taskIdentityPath;
    }

    @Override
    protected void nodeSpecificHealthDiagnostics(StringBuilder builder) {
        super.nodeSpecificHealthDiagnostics(builder);
        builder.append(", target=[").append(getTargetNode().healthDiagnostics()).append("]");
    }

    @Override
    public void execute(NodeExecutionContext context) {
        // This node does not do anything itself
    }

}
