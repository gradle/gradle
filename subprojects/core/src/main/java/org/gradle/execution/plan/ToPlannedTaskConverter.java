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

import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.initialization.DefaultPlannedTask;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToPlannedTaskConverter implements ToPlannedNodeConverter {

    @Override
    public Class<? extends Node> getSupportedNodeType() {
        return TaskNode.class;
    }

    @Override
    public CalculateTaskGraphBuildOperationType.TaskIdentity getNodeIdentity(Node node) {
        TaskNode taskNode = (TaskNode) node;
        TaskIdentity<?> delegate = taskNode.getTask().getTaskIdentity();
        return new CalculateTaskGraphBuildOperationType.TaskIdentity() {
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

            @Override
            public String toString() {
                return "Task " + delegate.getTaskPath();
            }
        };
    }

    @Override
    public boolean isInSamePlan(Node node) {
        return node instanceof LocalTaskNode;
    }

    @Override
    public CalculateTaskGraphBuildOperationType.PlannedTask convert(Node node, Function<Node, List<? extends CalculateTaskGraphBuildOperationType.NodeIdentity>> findDependencies) {
        if (!isInSamePlan(node)) {
            throw new IllegalArgumentException("Cannot convert task from another plan: " + node);
        }

        LocalTaskNode taskNode = (LocalTaskNode) node;
        return new DefaultPlannedTask(
            getNodeIdentity(taskNode),
            findDependencies.apply(taskNode),
            taskIdentifiesOf(taskNode.getMustSuccessors()),
            taskIdentifiesOf(taskNode.getShouldSuccessors()),
            taskIdentifiesOf(taskNode.getFinalizers())
        );
    }

    private List<CalculateTaskGraphBuildOperationType.TaskIdentity> taskIdentifiesOf(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return nodes.stream()
            .filter(TaskNode.class::isInstance)
            .map(TaskNode.class::cast)
            .map(this::getNodeIdentity)
            .collect(Collectors.toList());
    }
}
