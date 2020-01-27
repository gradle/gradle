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
import org.gradle.internal.taskgraph.PlannedTask;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
                public List<PlannedTask> getTaskPlan() {
                    List<Node> scheduledWork = taskGraph.getScheduledWork();
                    Set<DefaultPlannedTask> taskPlan = toPlannedTasks(scheduledWork);
                    return new ArrayList(taskPlan);
                }

                private Set<DefaultPlannedTask> toPlannedTasks(List<Node> scheduledWork) {
                    Set<DefaultPlannedTask> plannedTasks = new LinkedHashSet<>();
                    for (Node node : scheduledWork) {
                        if (node instanceof TaskNode) {
                            TaskNode taskNode = (TaskNode) node;
                            plannedTasks.add(toPlannedTask(taskNode));
                        }
                    }

                    return plannedTasks;
                }

                private DefaultPlannedTask toPlannedTask(TaskNode taskNode) {
                    TaskIdentity<?> taskIdentity = taskNode.getTask().getTaskIdentity();
                    return new DefaultPlannedTask(taskIdentity,
                        transformToIdentities(taskNode.getDependencySuccessors(), node -> node.getDependencySuccessors()),
                        transformToIdentities(taskNode.getMustSuccessors(), node -> Collections.emptySet()),
                        transformToIdentities(taskNode.getShouldSuccessors(), node -> Collections.emptySet()),
                        transformToIdentities(taskNode.getFinalizers(), node -> Collections.emptySet()));
                }

                private List<TaskIdentity> transformToIdentities(Set<Node> nodes, Function<Node, Set<Node>> nestedNodesResolver) {
                    return new ArrayList(CollectionUtils.collect(toOnlyTaskNodes(nodes, nestedNodesResolver), node -> ((TaskNode) node).getTask().getTaskIdentity()));
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, new Function<Task, String>() {
                        @Override
                        public String apply(Task task) {
                            return task.getPath();
                        }
                    })).asList();
                }
            });
        }

        private List<Node> toOnlyTaskNodes(Set<Node> nodes, Function<Node, Set<Node>> nestedNodesResolver) {
            List<Node> flattenAllNodes = CollectionUtils.flattenCollections(Node.class, CollectionUtils.collect(nodes, node -> node instanceof TaskNode ? node : nestedNodesResolver.apply(node)));
            return CollectionUtils.filter(flattenAllNodes, node -> node instanceof TaskNode);
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
}
