/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.taskgraph;

import org.gradle.internal.operations.BuildOperationType;

import java.util.List;
import java.util.Set;

/**
 * Computing the execution plan for a given build in the build tree based on the inputs and build configuration.
 * <p>
 * Despite the name, the execution plan is not limited to tasks. It can also include artifact transforms.
 * See {@link NodeIdentity.NodeType} for the full list of possible node types in the plan.
 *
 * @since 4.0
 */
public final class CalculateTaskGraphBuildOperationType implements BuildOperationType<CalculateTaskGraphBuildOperationType.Details, CalculateTaskGraphBuildOperationType.Result> {

    /**
     * An identifiable node in the execution graph with its dependencies.
     *
     * @since 8.1
     */
    public interface PlannedNode {

        NodeIdentity getNodeIdentity();

        /**
         * Returns the dependencies of this node.
         * <p>
         * Note that dependencies are not necessarily located in the same execution plan.
         */
        List<? extends NodeIdentity> getNodeDependencies();

    }

    /**
     * Identity of a local task node in the execution graph.
     *
     * @since 6.2
     */
    public interface TaskIdentity extends NodeIdentity {

        String getBuildPath();

        String getTaskPath();

        /**
         * See {@code org.gradle.api.internal.project.taskfactory.TaskIdentity#uniqueId}.
         */
        long getTaskId();

    }

    /**
     * A {@link PlannedNode} for a task in the execution graph.
     *
     * @since 6.2
     */
    public interface PlannedTask extends PlannedNode {

        TaskIdentity getTask();

        /**
         * @deprecated Use {@link #getNodeDependencies()} instead.
         */
        @Deprecated
        List<TaskIdentity> getDependencies();

        List<TaskIdentity> getMustRunAfter();

        List<TaskIdentity> getShouldRunAfter();

        List<TaskIdentity> getFinalizedBy();

    }

    public interface Details {

        /**
         * The build path the calculated task graph belongs too.
         * Never null.
         *
         * @since 4.5
         */
        String getBuildPath();
    }

    public interface Result {

        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        List<String> getRequestedTaskPaths();

        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        List<String> getExcludedTaskPaths();

        /**
         * Capturing task execution plan details.
         *
         * @since 6.2
         */
        List<PlannedTask> getTaskPlan();

        /**
         * Returns an execution plan consisting of nodes of the given types.
         * <p>
         * The graph is represented as a list of nodes (in no particular order) and their {@link PlannedNode#getNodeDependencies() dependencies}.
         * The dependencies of each node are the closest nodes in the plan whose type is in the given set.
         *
         * @param types an inclusive range-subset of node types starting with the {@link NodeIdentity.NodeType#TASK TASK}, such as {@code [TASK, TRANSFORM_STEP]}
         * @since 8.1
         */
        List<PlannedNode> getExecutionPlan(Set<NodeIdentity.NodeType> types);
    }

    private CalculateTaskGraphBuildOperationType() {
    }

}
