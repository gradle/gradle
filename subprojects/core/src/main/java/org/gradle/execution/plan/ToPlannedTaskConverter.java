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

import org.gradle.api.NonNullApi;
import org.gradle.initialization.DefaultPlannedTask;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.TaskIdentity;
import org.gradle.internal.taskgraph.NodeIdentity;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link ToPlannedTaskConverter} for {@link TaskNode}s.
 * <p>
 * Only can convert {@link LocalTaskNode}s to planned tasks.
 */
@NonNullApi
public class ToPlannedTaskConverter implements ToPlannedNodeConverter {

    @Override
    public Class<? extends Node> getSupportedNodeType() {
        return TaskNode.class;
    }

    @Override
    public NodeIdentity.NodeType getConvertedNodeType() {
        return NodeIdentity.NodeType.TASK;
    }

    @Override
    public TaskIdentity getNodeIdentity(Node node) {
        TaskNode taskNode = (TaskNode) node;
        return new PlannedTaskIdentity(taskNode.getTask().getTaskIdentity());
    }

    private static class PlannedTaskIdentity implements TaskIdentity {
        private final org.gradle.api.internal.project.taskfactory.TaskIdentity<?> delegate;

        public PlannedTaskIdentity(org.gradle.api.internal.project.taskfactory.TaskIdentity<?> delegate) {
            this.delegate = delegate;
        }

        @Override
        public NodeIdentity.NodeType getNodeType() {
            return NodeIdentity.NodeType.TASK;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PlannedTaskIdentity)) {
                return false;
            }
            PlannedTaskIdentity that = (PlannedTaskIdentity) o;
            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate);
        }

        @Override
        public String toString() {
            return "Task " + delegate.getIdentityPath();
        }
    }

    @Override
    public boolean isInSamePlan(Node node) {
        return node instanceof LocalTaskNode;
    }

    @Override
    public DefaultPlannedTask convert(Node node, List<? extends NodeIdentity> nodeDependencies) {
        if (!isInSamePlan(node)) {
            throw new IllegalArgumentException("Cannot convert task from another plan: " + node);
        }

        LocalTaskNode taskNode = (LocalTaskNode) node;
        return new DefaultPlannedTask(
            getNodeIdentity(taskNode),
            nodeDependencies,
            getTaskIdentities(taskNode.getMustSuccessors()),
            getTaskIdentities(taskNode.getShouldSuccessors()),
            getTaskIdentities(taskNode.getFinalizers())
        );
    }

    private List<TaskIdentity> getTaskIdentities(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return nodes.stream()
            .filter(TaskNode.class::isInstance)
            .map(TaskNode.class::cast)
            .map(this::getNodeIdentity)
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "ToPlannedTaskConverter(" + getSupportedNodeType().getSimpleName() + ")";
    }
}
