/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution.workgraph;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.internal.scheduler.Edge;
import org.gradle.internal.scheduler.EdgeType;
import org.gradle.internal.scheduler.Graph;
import org.gradle.internal.scheduler.Node;

import java.util.Set;

public class WorkGraph {
    private final Graph graph;
    private final Graph originalGraph;
    private final ImmutableMap<Task, TaskNode> taskNodes;
    private final ImmutableSet<TaskNode> requestedNodes;
    private final ImmutableSet<Task> filteredTasks;

    public WorkGraph(Graph graph, ImmutableMap<Task, TaskNode> taskNodes, ImmutableSet<TaskNode> requestedNodes, ImmutableSet<Task> filteredTasks) {
        this.graph = graph;
        this.originalGraph = new Graph(graph);
        this.taskNodes = taskNodes;
        this.requestedNodes = requestedNodes;
        this.filteredTasks = filteredTasks;
    }

    public Graph getGraph() {
        return graph;
    }

    public ImmutableSet<Task> getAllTasks() {
        return taskNodes.keySet();
    }

    public ImmutableCollection<TaskNode> getAllNodes() {
        return taskNodes.values();
    }

    public ImmutableMap<Task, TaskNode> getTaskNodes() {
        return taskNodes;
    }

    public ImmutableSet<TaskNode> getRequestedNodes() {
        return requestedNodes;
    }

    public ImmutableSet<Task> getRequestedTasks() {
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (TaskNode requestedNode : requestedNodes) {
            builder.add(requestedNode.getTask());
        }
        return builder.build();
    }

    public ImmutableSet<Task> getFilteredTasks() {
        return filteredTasks;
    }

    public boolean hasTask(Task task) {
        return taskNodes.containsKey(task);
    }

    public boolean hasTask(String path) {
        for (Task task : taskNodes.keySet()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public Set<Task> getDirectTaskDependencies(Task task) {
        TaskNode taskNode = taskNodes.get(task);
        if (taskNode == null) {
            throw new IllegalArgumentException("Task is not part of work graph: " + task);
        }
        ImmutableSet.Builder<Task> dependencies = ImmutableSet.builder();
        for (Edge edge : originalGraph.getIncomingEdges(taskNode)) {
            if (edge.getType() == EdgeType.DEPENDENCY_OF) {
                Node source = edge.getSource();
                if (source instanceof TaskNode) {
                    dependencies.add(((TaskNode) source).getTask());
                }
            }
        }
        return dependencies.build();
    }
}
