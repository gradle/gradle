/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.execution;

import com.google.common.collect.Streams;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.provider.ConfigurationTimeBarrier;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskInAnotherBuild;
import org.gradle.execution.plan.TaskNode;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.graph.GraphNodeRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.IncubationLogger;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link BuildWorkExecutor} that does not execute any tasks, but prints the task graph instead.
 */
public class TaskGraphBuildExecutionAction implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final StyledTextOutputFactory textOutputFactory;
    private final ConfigurationTimeBarrier configurationTimeBarrier;

    public TaskGraphBuildExecutionAction(
        BuildWorkExecutor delegate,
        StyledTextOutputFactory textOutputFactory,
        ConfigurationTimeBarrier configurationTimeBarrier
    ) {
        this.delegate = delegate;
        this.textOutputFactory = textOutputFactory;
        this.configurationTimeBarrier = configurationTimeBarrier;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        if (configurationTimeBarrier.isAtConfigurationTime()) {
            return delegate.execute(gradle, plan);
        }

        // The task sub-graph from an included build will be traversed and printed from the root build as well
        if (gradle.isRootBuild()) {
            IncubationLogger.incubatingFeatureUsed("Task graph printing");
            renderTaskGraph(gradle, plan);
        }

        return ExecutionResult.succeeded();
    }

    private void renderTaskGraph(GradleInternal gradle, FinalizedExecutionPlan plan) {
        plan.getContents().getScheduledNodes().visitNodes((nodes, entryNodes) -> {
            String invocation = renderRequestedTasks(gradle.getStartParameter());
            StyledTextOutput output = textOutputFactory.create(TaskGraphBuildExecutionAction.class);
            DirectedGraphRenderer<TaskInfo> renderer = new DirectedGraphRenderer<>(new NodeRenderer(), new NodesGraph());
            renderer.renderTo(new RootNode(entryNodes, invocation), output);
        });
    }

    private static String renderRequestedTasks(StartParameterInternal startParameter) {
        return startParameter
            .getTaskRequests()
            .stream()
            .map(TaskExecutionRequest::getArgs)
            .flatMap(List::stream)
            .collect(Collectors.joining(" "));
    }

    private static class NodeRenderer implements GraphNodeRenderer<TaskInfo> {

        @Override
        public void renderTo(TaskInfo node, StyledTextOutput output, boolean alreadySeen) {
            output
                .withStyle(StyledTextOutput.Style.Identifier).text(node.getId());

            if (!alreadySeen) {
                String description = node.getDescription();
                if (!description.isEmpty()) {
                    output.text(" ");
                    output.withStyle(StyledTextOutput.Style.Description).text(description);
                }
            }
        }
    }

    private static class NodesGraph implements DirectedGraph<TaskInfo, TaskInfo> {

        @Override
        public void getNodeValues(TaskInfo node, Collection<? super TaskInfo> values, Collection<? super TaskInfo> connectedNodes) {
            values.add(node);
            connectedNodes.addAll(node.getDependencies());
        }
    }

    private static Stream<TaskInfo> extractTaskNodes(Collection<Node> collection, DependencyType type) {
        return collection.stream()
            .filter(node -> node instanceof TaskNode && !node.isDoNotIncludeInPlan())
            .map(taskNode -> new DefaultTaskInfo((TaskNode) taskNode, type));
    }

    private enum DependencyType {
        REGULAR,
        FINALIZING,
    }

    private interface TaskInfo {
        String getId();

        String getDescription();

        Collection<TaskInfo> getDependencies();
    }

    private static class RootNode implements TaskInfo {
        private final Collection<Node> entryNodes;
        private final String invocationInfo;

        public RootNode(Collection<Node> entryNodes, String invocationInfo) {
            this.entryNodes = entryNodes;
            this.invocationInfo = invocationInfo;
        }

        @Override
        public String getId() {
            return "Tasks graph for: " + invocationInfo;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public Collection<TaskInfo> getDependencies() {
            return extractTaskNodes(entryNodes, DependencyType.REGULAR)
                .collect(Collectors.toList());
        }
    }

    private static class DefaultTaskInfo implements TaskInfo {
        private final TaskNode node;
        private final DependencyType type;

        DefaultTaskInfo(TaskNode node, DependencyType type) {
            this.node = node;
            this.type = type;
        }

        @Override
        public String getId() {
            return node.toString();
        }

        @Override
        public String getDescription() {
            StringBuilder description = new StringBuilder();
            description.append("(");
            description.append(node.getTask().getTaskIdentity().getTaskType().getCanonicalName());
            if (type.equals(DependencyType.FINALIZING)) {
                description.append(", finalizer");
            }
            if (!node.getTask().getEnabled()) {
                description.append(", disabled");
            }
            description.append(")");
            return description.toString();
        }

        @Override
        public Collection<TaskInfo> getDependencies() {
            TaskNode targetNode = node;
            if (node instanceof TaskInAnotherBuild) {
                targetNode = ((TaskInAnotherBuild) node).getTargetNode();
            }
            return Streams.concat(
                extractTaskNodes(targetNode.getDependencySuccessors(), DependencyType.REGULAR),
                extractTaskNodes(targetNode.getFinalizers(), DependencyType.FINALIZING)
            ).collect(Collectors.toList());
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultTaskInfo that = (DefaultTaskInfo) o;
            return node.equals(that.node);
        }
    }
}
