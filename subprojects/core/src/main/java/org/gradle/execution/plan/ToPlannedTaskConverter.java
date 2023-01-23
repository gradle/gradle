/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.initialization.DefaultPlannedTask;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToPlannedTaskConverter implements ToPlannedNodeConverter {

    @Override
    public Class<?> getSupportedNodeType() {
        return LocalTaskNode.class;
    }

    @Override
    public CalculateTaskGraphBuildOperationType.PlannedNode convertToPlannedNode(Node node, Function<Node, List<? extends CalculateTaskGraphBuildOperationType.NodeIdentity>> findDependencies) {
        LocalTaskNode taskNode = (LocalTaskNode) node;
        return new DefaultPlannedTask(
            taskNode.getNodeIdentity(),
            findDependencies.apply(taskNode),
            taskIdentifiesOf(taskNode.getMustSuccessors()),
            taskIdentifiesOf(taskNode.getShouldSuccessors()),
            taskIdentifiesOf(taskNode.getFinalizers())
        );
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
}
