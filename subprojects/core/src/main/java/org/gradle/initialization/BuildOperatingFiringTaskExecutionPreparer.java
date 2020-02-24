/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildOperatingFiringTaskExecutionPreparer implements TaskExecutionPreparer {
    private final TaskExecutionPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperatingFiringTaskExecutionPreparer(TaskExecutionPreparer delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void prepareForTaskExecution(GradleInternal gradle) {
        buildOperationExecutor.run(new CalculateTaskGraph(gradle));
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public CalculateTaskGraph(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext buildOperationContext) {
            final TaskExecutionGraphInternal taskGraph = populateTaskGraph();

            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(taskGraph.getRequestedTasks());
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(taskGraph.getFilteredTasks());
                }

                @Override
                public List<CalculateTaskGraphBuildOperationType.PlannedTask> getTaskPlan() {
                    List<Node> scheduledWork = taskGraph.getScheduledWork();
                    return toPlannedTasks(scheduledWork);
                }

                private List<CalculateTaskGraphBuildOperationType.PlannedTask> toPlannedTasks(List<Node> scheduledWork) {
                    List<CalculateTaskGraphBuildOperationType.PlannedTask> plannedTasks = new ArrayList<>();
                    for (Node node : scheduledWork) {
                        if (node instanceof TaskNode) {
                            TaskNode taskNode = (TaskNode) node;
                            plannedTasks.add(toPlannedTask(taskNode));
                        }
                    }

                    return plannedTasks;
                }

                private CalculateTaskGraphBuildOperationType.PlannedTask toPlannedTask(TaskNode taskNode) {
                    TaskIdentity<?> taskIdentity = taskNode.getTask().getTaskIdentity();
                    return new DefaultPlannedTask(new PlannedTaskIdentity(taskIdentity),
                        transformToIdentities(taskNode.getDependencySuccessors(), node -> node.getDependencySuccessors()),
                        transformToIdentities(taskNode.getMustSuccessors(), node -> Collections.emptySet()),
                        transformToIdentities(taskNode.getShouldSuccessors(), node -> Collections.emptySet()),
                        transformToIdentities(taskNode.getFinalizers(), node -> Collections.emptySet()));
                }

                private List<CalculateTaskGraphBuildOperationType.TaskIdentity> transformToIdentities(Set<Node> nodes, Function<Node, Set<Node>> nestedNodesResolver) {
                    return toOnlyTaskNodes(nodes, nestedNodesResolver).stream().map(node -> ((TaskNode) node).getTask().getTaskIdentity())
                        .map(id -> new PlannedTaskIdentity(id)).collect(Collectors.toList());
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, (Function<Task, String>) task -> task.getPath())).asList();
                }
            });
        }

        private List<Node> toOnlyTaskNodes(Set<Node> nodes, Function<Node, Set<Node>> nestedNodesResolver) {
            return nodes.stream()
                .flatMap(node -> node instanceof TaskNode ? Stream.of(node) : toOnlyTaskNodes(nestedNodesResolver.apply(node), nestedNodesResolver).stream())
                .filter(node -> node instanceof TaskNode)
                .collect(Collectors.toList());
        }

        TaskExecutionGraphInternal populateTaskGraph() {
            delegate.prepareForTaskExecution(gradle);
            return gradle.getTaskGraph();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().getPath();
                    }
                });
        }
    }

    private static class PlannedTaskIdentity implements CalculateTaskGraphBuildOperationType.TaskIdentity {

        private final TaskIdentity delegate;

        public PlannedTaskIdentity(TaskIdentity delegate) {
            this.delegate = delegate;
        }
        @Override
        public String getBuildPath() {
            return delegate.getBuildPath();
        }

        @Override
        public String getTaskPath() {
            return delegate.getTaskPath();
        }

        @Override
        public long getTaskId() {
            return delegate.getId();
        }
    }
}
