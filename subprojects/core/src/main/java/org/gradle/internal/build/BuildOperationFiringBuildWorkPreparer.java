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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry;
import org.gradle.internal.taskgraph.NodeIdentity;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.Details;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedTask;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.Result;

@NonNullApi
public class BuildOperationFiringBuildWorkPreparer implements BuildWorkPreparer {
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildWorkPreparer delegate;
    private final ToPlannedNodeConverterRegistry converterRegistry;

    public BuildOperationFiringBuildWorkPreparer(BuildOperationExecutor buildOperationExecutor, BuildWorkPreparer delegate, ToPlannedNodeConverterRegistry converterRegistry) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        this.converterRegistry = converterRegistry;
    }

    @Override
    public ExecutionPlan newExecutionPlan() {
        return delegate.newExecutionPlan();
    }

    @Override
    public void populateWorkGraph(GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action) {
        buildOperationExecutor.run(new PopulateWorkGraph(delegate, gradle, plan, action, converterRegistry));
    }

    @Override
    public FinalizedExecutionPlan finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan) {
        return delegate.finalizeWorkGraph(gradle, plan);
    }

    private static class PopulateWorkGraph implements RunnableBuildOperation {
        private final BuildWorkPreparer delegate;
        private final GradleInternal gradle;
        private final ExecutionPlan plan;
        private final Consumer<? super ExecutionPlan> action;
        private final ToPlannedNodeConverterRegistry converterRegistry;

        public PopulateWorkGraph(BuildWorkPreparer delegate, GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action, ToPlannedNodeConverterRegistry converterRegistry) {
            this.delegate = delegate;
            this.gradle = gradle;
            this.plan = plan;
            this.action = action;
            this.converterRegistry = converterRegistry;
        }

        @Override
        public void run(BuildOperationContext buildOperationContext) {
            populateTaskGraph();

            // create copy now - https://github.com/gradle/gradle/issues/12527
            QueryableExecutionPlan contents = plan.getContents();
            Set<Task> requestedTasks = contents.getRequestedTasks();
            Set<Task> filteredTasks = contents.getFilteredTasks();
            QueryableExecutionPlan.ScheduledNodes scheduledWork = contents.getScheduledNodes();

            PlannedNodeGraph plannedNodeGraph = computePlannedNodeGraph(scheduledWork);

            buildOperationContext.setResult(new CalculateTaskGraphResult(requestedTasks, filteredTasks, plannedNodeGraph));
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

        private PlannedNodeGraph computePlannedNodeGraph(QueryableExecutionPlan.ScheduledNodes scheduledWork) {
            PlannedNodeGraph.Collector collector = new PlannedNodeGraph.Collector(converterRegistry);
            scheduledWork.visitNodes(collector::collectNodes);
            return collector.getGraph();
        }

        private static class CalculateTaskGraphResult implements Result, CustomOperationTraceSerialization {

            private final Set<Task> requestedTasks;
            private final Set<Task> filteredTasks;
            private final PlannedNodeGraph plannedNodeGraph;

            public CalculateTaskGraphResult(Set<Task> requestedTasks, Set<Task> filteredTasks, PlannedNodeGraph plannedNodeGraph) {
                this.requestedTasks = requestedTasks;
                this.filteredTasks = filteredTasks;
                this.plannedNodeGraph = plannedNodeGraph;
            }

            @Override
            public List<String> getRequestedTaskPaths() {
                return toUniqueSortedTaskPaths(requestedTasks);
            }

            @Override
            public List<String> getExcludedTaskPaths() {
                return toUniqueSortedTaskPaths(filteredTasks);
            }

            @Override
            public List<PlannedTask> getTaskPlan() {
                @SuppressWarnings("unchecked")
                List<? extends PlannedTask> taskPlan = (List<? extends PlannedTask>) plannedNodeGraph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL1_TASKS);
                return ImmutableList.copyOf(taskPlan);
            }

            @Override
            public List<PlannedNode> getExecutionPlan(Set<NodeIdentity.NodeType> types) {
                if (types.isEmpty()) {
                    return Collections.emptyList();
                }

                @SuppressWarnings("unchecked")
                List<PlannedNode> plan = (List<PlannedNode>) plannedNodeGraph.getNodes(PlannedNodeGraph.DetailLevel.from(types));
                return plan;
            }

            @Override
            public Object getCustomOperationTraceSerializableModel() {
                ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
                builder.put("requestedTaskPaths", getRequestedTaskPaths());
                builder.put("excludedTaskPaths", getExcludedTaskPaths());
                builder.put("taskPlan", getTaskPlan());
                builder.put("executionPlan", getExecutionPlan(EnumSet.allOf(NodeIdentity.NodeType.class)));
                return builder.build();
            }

            private static List<String> toUniqueSortedTaskPaths(Set<Task> tasks) {
                return tasks.stream().map(Task::getPath).distinct().sorted().collect(Collectors.toList());
            }
        }
    }

}
