/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.initialization.DefaultPlannedTask;
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
import java.util.function.Consumer;

import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNull;

@SuppressWarnings({"Guava"})
public class BuildOperationFiringBuildWorkPreparer implements BuildWorkPreparer {
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildWorkPreparer delegate;

    public BuildOperationFiringBuildWorkPreparer(BuildOperationExecutor buildOperationExecutor, BuildWorkPreparer delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Override
    public ExecutionPlan newExecutionPlan() {
        return delegate.newExecutionPlan();
    }

    @Override
    public void populateWorkGraph(GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action) {
        buildOperationExecutor.run(new PopulateWorkGraph(gradle, plan, delegate, action));
    }

    @Override
    public void finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan) {
        delegate.finalizeWorkGraph(gradle, plan);
    }

    private static class PopulateWorkGraph implements RunnableBuildOperation {
        private final GradleInternal gradle;
        private final ExecutionPlan plan;
        private final BuildWorkPreparer delegate;
        private final Consumer<? super ExecutionPlan> action;

        public PopulateWorkGraph(GradleInternal gradle, ExecutionPlan plan, BuildWorkPreparer delegate, Consumer<? super ExecutionPlan> action) {
            this.gradle = gradle;
            this.plan = plan;
            this.delegate = delegate;
            this.action = action;
        }

        @Override
        public void run(BuildOperationContext buildOperationContext) {
            populateTaskGraph();

            // create copy now - https://github.com/gradle/gradle/issues/12527
            Set<Task> requestedTasks = plan.getRequestedTasks();
            Set<Task> filteredTasks = plan.getFilteredTasks();
            ExecutionPlan.ScheduledNodes scheduledWork = plan.getScheduledNodes();

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

                private List<CalculateTaskGraphBuildOperationType.PlannedTask> toPlannedTasks(ExecutionPlan.ScheduledNodes scheduledWork) {
                    List<CalculateTaskGraphBuildOperationType.PlannedTask> tasks = new ArrayList<>();
                    scheduledWork.visitNodes(nodes -> {
                        for (Node node : nodes) {
                            if (node instanceof LocalTaskNode) {
                                tasks.add(toPlannedTask((LocalTaskNode) node));
                            }
                        }
                    });
                    return tasks;
                }

                private CalculateTaskGraphBuildOperationType.PlannedTask toPlannedTask(LocalTaskNode taskNode) {
                    TaskIdentity<?> taskIdentity = taskNode.getTask().getTaskIdentity();
                    return new DefaultPlannedTask(
                        new PlannedTaskIdentity(taskIdentity),
                        taskIdentifiesOf(taskNode.getDependencySuccessors(), Node::getDependencySuccessors),
                        taskIdentifiesOf(taskNode.getMustSuccessors()),
                        taskIdentifiesOf(taskNode.getShouldSuccessors()),
                        taskIdentifiesOf(taskNode.getFinalizers())
                    );
                }
            });
        }

        void populateTaskGraph() {
            delegate.populateWorkGraph(gradle, plan, action);
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

    private static List<CalculateTaskGraphBuildOperationType.TaskIdentity> taskIdentifiesOf(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<CalculateTaskGraphBuildOperationType.TaskIdentity> list = new ArrayList<>();
        traverseNonTasks(nodes, traverser, newSetFromMap(new IdentityHashMap<>()))
            .forEach(taskNode -> list.add(toIdentity(taskNode)));
        return list;
    }

    private static Iterable<TaskNode> traverseNonTasks(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser, Set<Node> seen) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return FluentIterable.from(nodes)
            .filter(seen::add)
            .transformAndConcat(
                node -> node instanceof TaskNode
                    ? ImmutableSet.of((TaskNode) node)
                    : traverseNonTasks(requireNonNull(traverser.apply(node)), traverser, seen)
            );
    }

    private static List<CalculateTaskGraphBuildOperationType.TaskIdentity> taskIdentifiesOf(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return FluentIterable.from(nodes)
            .filter(TaskNode.class)
            .transform(BuildOperationFiringBuildWorkPreparer::toIdentity)
            .toList();
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
