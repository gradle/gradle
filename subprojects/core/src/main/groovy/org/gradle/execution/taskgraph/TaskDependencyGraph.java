/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class TaskDependencyGraph {

    private final LinkedHashMap<Task, TaskDependencyGraphNode> nodes = new LinkedHashMap<Task, TaskDependencyGraphNode>();
    private final List<TaskDependencyGraphNode> nodesWithoutIncomingEdges = new ArrayList<TaskDependencyGraphNode>();

    public List<TaskDependencyGraphNode> getNodesWithoutIncomingEdges() {
        return nodesWithoutIncomingEdges;
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public boolean hasTask(Task task) {
        return getTasks().contains(task);
    }

    public void addEdge(Task fromTask, Task toTask) {
        addEdge(fromTask, toTask, true);
    }

    public void addEdge(Task fromTask, Task toTask, boolean toTaskIsRequired) {
        TaskDependencyGraphNode fromNode;
        TaskDependencyGraphNode toNode;
        if (!hasTask(fromTask)) {
            fromNode = createNode(fromTask, true);
            nodesWithoutIncomingEdges.add(fromNode);
        } else {
            fromNode = nodes.get(fromTask);
            fromNode.setRequired(true);
        }
        if (!hasTask(toTask)) {
            toNode = createNode(toTask, toTaskIsRequired);
        } else {
            toNode = nodes.get(toTask);
            toNode.setRequired(toNode.getRequired() || toTaskIsRequired);
            nodesWithoutIncomingEdges.remove(toNode);
        }
        fromNode.addEdgeTo(toNode);
    }

    public void addNode(Task task) {
        if (!hasTask(task)) {
            TaskDependencyGraphNode node = createNode(task, true);
            nodesWithoutIncomingEdges.add(node);
        } else {
            getNode(task).setRequired(true);
        }
    }

    public TaskDependencyGraphNode getNode(Task task) {
        return nodes.get(task);
    }

    private TaskDependencyGraphNode createNode(Task task, boolean required) {
        TaskDependencyGraphNode node = new TaskDependencyGraphNode(task, required);
        nodes.put(task, node);
        return node;
    }

    public void clear() {
        nodes.clear();
        nodesWithoutIncomingEdges.clear();
    }
}
