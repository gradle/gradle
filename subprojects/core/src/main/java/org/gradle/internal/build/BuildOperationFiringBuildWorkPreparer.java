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

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.BaseTransformationNode;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.execution.plan.TaskNode;
import org.gradle.initialization.DefaultPlannedTask;
import org.gradle.initialization.DefaultPlannedTransform;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.newSetFromMap;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.*;

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
    public FinalizedExecutionPlan finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan) {
        return delegate.finalizeWorkGraph(gradle, plan);
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
            QueryableExecutionPlan contents = plan.getContents();
            Set<Task> requestedTasks = contents.getRequestedTasks();
            Set<Task> filteredTasks = contents.getFilteredTasks();
            QueryableExecutionPlan.ScheduledNodes scheduledWork = contents.getScheduledNodes();

            List<PlannedNode> plannedNodes = toPlannedNodes(scheduledWork);
            List<PlannedTask> plannedTasks = plannedNodes.stream()
                .filter(PlannedTask.class::isInstance)
                .map(PlannedTask.class::cast)
                .collect(Collectors.toList());

            buildOperationContext.setResult(new Result() {

                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(requestedTasks);
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(filteredTasks);
                }

                @Override
                public List<PlannedTask> getTaskPlan() {
                    return plannedTasks;
                }

                @Override
                public List<PlannedNode> getExecutionPlan() {
                    return plannedNodes;
                }
            });
        }

        void populateTaskGraph() {
            delegate.populateWorkGraph(gradle, plan, action);
        }

        @Nonnull
        @Override
        public BuildOperationDescriptor.Builder description() {
            //noinspection Convert2Lambda
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().getPath();
                    }
                });
        }
    }

    private static List<PlannedNode> toPlannedNodes(QueryableExecutionPlan.ScheduledNodes scheduledWork) {
        List<PlannedNode> plannedNodes = new ArrayList<>();
        scheduledWork.visitNodes(nodes -> {
            for (Node node : nodes) {
                if (node instanceof LocalTaskNode) { // TODO: why Local and not TaskNode?
                    PlannedTask plannedTask = toPlannedTask((LocalTaskNode) node);
                    plannedNodes.add(plannedTask);
                } else if (node instanceof BaseTransformationNode) {
                    plannedNodes.add(toPlannedTransform((BaseTransformationNode) node));
                }
            }
        });
        return plannedNodes;
    }

    private static PlannedNode toPlannedTransform(BaseTransformationNode node) {
        return new DefaultPlannedTransform(
            node.getNodeIdentity(),
            findDependencies(node)
        );
    }

    private static PlannedTask toPlannedTask(LocalTaskNode taskNode) {
        return new DefaultPlannedTask(
            taskNode.getNodeIdentity(),
            findDependencies(taskNode),
            taskIdentifiesOf(taskNode.getMustSuccessors()),
            taskIdentifiesOf(taskNode.getShouldSuccessors()),
            taskIdentifiesOf(taskNode.getFinalizers())
        );
    }

    private static List<? extends NodeIdentity> findDependencies(Node node) {
        return findDependencies(node.getDependencySuccessors(), Node::getDependencySuccessors);
    }

    private static List<? extends NodeIdentity> findDependencies(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return findIdentifiedNodes(nodes, traverser, newSetFromMap(new IdentityHashMap<>()))
            .collect(Collectors.toList());
    }

    private static Stream<NodeIdentity> findIdentifiedNodes(Collection<Node> nodes, Function<? super Node, ? extends Collection<Node>> traverser, Set<Node> seen) {
        if (nodes.isEmpty()) {
            return Stream.empty();
        }

        // TODO: do we stop at transform nodes or do we continue until we reach tasks anyway?
        return nodes.stream()
            .filter(seen::add)
            .flatMap(node -> {
                NodeIdentity identity = node.getNodeIdentity();
                return identity != null
                    ? Stream.of(identity)
                    : findIdentifiedNodes(traverser.apply(node), traverser, seen);
            });
    }

    private static List<CalculateTaskGraphBuildOperationType.TaskIdentity> taskIdentifiesOf(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return nodes.stream()
            .filter(TaskNode.class::isInstance)
            .map(TaskNode.class::cast)
            .map(TaskNode::getNodeIdentity)
            .collect(Collectors.toList());
    }

    private static List<String> toTaskPaths(Set<Task> tasks) {
        return tasks.stream().map(Task::getPath).distinct().collect(Collectors.toList());
    }

}
