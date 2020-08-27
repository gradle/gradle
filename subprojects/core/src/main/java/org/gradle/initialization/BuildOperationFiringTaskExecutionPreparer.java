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
import com.google.common.collect.FluentIterable;
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
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

@SuppressWarnings({"Guava"})
public class BuildOperationFiringTaskExecutionPreparer implements TaskExecutionPreparer {
    private final TaskExecutionPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationFiringTaskExecutionPreparer(TaskExecutionPreparer delegate, BuildOperationExecutor buildOperationExecutor) {
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
            TaskExecutionGraphInternal taskGraph = populateTaskGraph();

            // create copy now - https://github.com/gradle/gradle/issues/12527
            Set<Task> requestedTasks = taskGraph.getRequestedTasks();
            Set<Task> filteredTasks = taskGraph.getFilteredTasks();
            List<Node> scheduledWork = taskGraph.getScheduledWork();

            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(requestedTasks);
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(filteredTasks);
                }

                @Override
                public List<CalculateTaskGraphBuildOperationType.PlannedTask> getTaskPlan() {
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
                    return new DefaultPlannedTask(
                        new PlannedTaskIdentity(taskIdentity),
                        traverseNonTasks(taskNode.getDependencySuccessors(), Node::getDependencySuccessors),
                        filterNonTasks(taskNode.getMustSuccessors()),
                        filterNonTasks(taskNode.getShouldSuccessors()),
                        filterNonTasks(taskNode.getFinalizers())
                    );
                }
            });
        }

        TaskExecutionGraphInternal populateTaskGraph() {
            delegate.prepareForTaskExecution(gradle);
            return gradle.getTaskGraph();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            //noinspection Convert2Lambda
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().getPath();
                    }
                });
        }
    }

    private static List<CalculateTaskGraphBuildOperationType.TaskIdentity> traverseNonTasks(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<CalculateTaskGraphBuildOperationType.TaskIdentity> list = new ArrayList<>();
            traverseNonTasks(nodes, traverser, Collections.newSetFromMap(new IdentityHashMap<>()))
                .forEach(taskNode -> list.add(toIdentity(taskNode)));

            return list;
        }
    }

    private static Iterable<TaskNode> traverseNonTasks(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser, Set<Node> seen) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return FluentIterable.from(nodes)
            .filter(seen::add)
            .transformAndConcat(
                node -> node instanceof TaskNode
                    ? Collections.singleton((TaskNode) node)
                    : traverseNonTasks(requireNonNull(traverser.apply(node)), traverser, seen)
            );
    }

    private static List<CalculateTaskGraphBuildOperationType.TaskIdentity> filterNonTasks(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        } else {
            return FluentIterable.from(nodes)
                .filter(TaskNode.class)
                .transform(BuildOperationFiringTaskExecutionPreparer::toIdentity)
                .toList();
        }
    }

    private static CalculateTaskGraphBuildOperationType.TaskIdentity toIdentity(TaskNode n) {
        return new PlannedTaskIdentity(n.getTask().getTaskIdentity());
    }

    private static List<String> toTaskPaths(Set<Task> tasks) {
        return ImmutableSortedSet.copyOf(Collections2.transform(tasks, Task::getPath)).asList();
    }

    private static class PlannedTaskIdentity implements CalculateTaskGraphBuildOperationType.TaskIdentity {

        private final TaskIdentity<?> delegate;

        public PlannedTaskIdentity(TaskIdentity<?> delegate) {
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
