/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.execution.ResolveTaskMutationsBuildOperationType;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;

public class ResolveMutationsNode extends Node implements SelfExecutingNode {
    private final LocalTaskNode node;
    private final NodeValidator nodeValidator;
    private final BuildOperationRunner buildOperationRunner;
    private final ExecutionNodeAccessHierarchies accessHierarchies;
    private Exception failure;

    public ResolveMutationsNode(LocalTaskNode node, NodeValidator nodeValidator, BuildOperationRunner buildOperationRunner, ExecutionNodeAccessHierarchies accessHierarchies) {
        this.node = node;
        this.nodeValidator = nodeValidator;
        this.buildOperationRunner = buildOperationRunner;
        this.accessHierarchies = accessHierarchies;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "Resolve mutations for " + node;
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return failure;
    }

    @Override
    public boolean isCanCancel() {
        return node.isCanCancel();
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        return node.getProjectToLock();
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return node.getOwningProject();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                try {
                    doResolveMutations();
                    context.setResult(RESOLVE_TASK_MUTATIONS_RESULT);
                } catch (Exception e) {
                    failure = e;
                    context.failed(failure);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                TaskIdentity<?> taskIdentity = node.getTask().getTaskIdentity();
                return BuildOperationDescriptor.displayName("Resolve mutations for task " + taskIdentity.getIdentityPath())
                    .details(new ResolveTaskMutationsDetails(taskIdentity));
            }
        });
    }

    private void doResolveMutations() {
        MutationInfo mutations = node.getMutationInfo();
        node.resolveMutations();
        mutations.hasValidationProblem = nodeValidator.hasValidationProblems(node);
        accessHierarchies.getOutputHierarchy().recordNodeAccessingLocations(node, mutations.outputPaths);
        accessHierarchies.getDestroyableHierarchy().recordNodeAccessingLocations(node, mutations.destroyablePaths);
    }

    private static final class ResolveTaskMutationsDetails implements ResolveTaskMutationsBuildOperationType.Details {
        private final TaskIdentity<?> taskIdentity;

        public ResolveTaskMutationsDetails(TaskIdentity<?> taskIdentity) {
            this.taskIdentity = taskIdentity;
        }

        @Override
        public String getBuildPath() {
            return taskIdentity.getBuildPath();
        }

        @Override
        public String getTaskPath() {
            return taskIdentity.getTaskPath();
        }

        @Override
        public long getTaskId() {
            return taskIdentity.getId();
        }
    }

    private static final ResolveTaskMutationsBuildOperationType.Result RESOLVE_TASK_MUTATIONS_RESULT = new ResolveTaskMutationsBuildOperationType.Result() {};
}
